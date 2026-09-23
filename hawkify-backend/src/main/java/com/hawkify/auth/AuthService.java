package com.hawkify.auth;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.admin.AlcanceService;
import com.hawkify.security.JwtService;
import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.Rol;
import com.hawkify.usuario.RolNombre;
import com.hawkify.usuario.RolRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'a las' h:mm a", Locale.of("es", "CO"));

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AlcanceService alcance;

    @Transactional
    public UsuarioResponse registrar(RegisterRequest request) {
        String correo = normalizarCorreo(request.correo());

        if (usuarioRepository.existsByCorreo(correo)) {
            throw new CorreoYaRegistradoException(correo);
        }

        Rol rolUsuarioFinal = rolRepository.findByNombre(RolNombre.USUARIO_FINAL)
                .orElseThrow(() -> new IllegalStateException(
                        "El rol base '" + RolNombre.USUARIO_FINAL + "' no existe en la base de datos"));

        Usuario usuario = Usuario.builder()
                .nombreCompleto(request.nombreCompleto().trim().replaceAll("\\s+", " "))
                .correo(correo)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(rolUsuarioFinal)
                .estado(EstadoUsuario.ACTIVO)
                .correoVerificado(false)
                .aceptoPoliticaEn(OffsetDateTime.now())
                .build();

        usuario = usuarioRepository.save(usuario);

        return aUsuarioResponse(usuario);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String correo = normalizarCorreo(request.correo());

        // RF-04: una suspension temporal vencida se levanta sola al intentar entrar.
        usuarioRepository.findByCorreoConRol(correo).ifPresent(u -> {
            if (u.getEstado() == EstadoUsuario.SUSPENDIDO && u.getSuspendidoHasta() != null
                    && u.getSuspendidoHasta().isBefore(OffsetDateTime.now())) {
                u.setEstado(EstadoUsuario.ACTIVO);
                u.setSuspendidoHasta(null);
                u.setMotivoEstado(null);
                usuarioRepository.saveAndFlush(u);
            }
        });

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(correo, request.password()));
        } catch (BadCredentialsException e) {
            throw new CredencialesInvalidasException();
        } catch (DisabledException e) {
            throw new CuentaNoDisponibleException(
                    "La cuenta está desactivada. Si crees que es un error, escribe a soporte@hawkify.co");
        } catch (LockedException e) {
            Usuario u = usuarioRepository.findByCorreoConRol(correo).orElseThrow(CredencialesInvalidasException::new);
            StringBuilder msg = new StringBuilder("Tu cuenta está suspendida");
            if (u.getSuspendidoHasta() != null) {
                msg.append(" hasta el ").append(FECHA_HORA.format(
                        u.getSuspendidoHasta().atZoneSameInstant(java.time.ZoneId.of("America/Bogota"))));
            }
            if (u.getMotivoEstado() != null) {
                msg.append(". Motivo: ").append(u.getMotivoEstado());
            }
            throw new CuentaNoDisponibleException(msg.toString());
        }

        Usuario usuario = usuarioRepository.findByCorreoConRol(correo)
                .orElseThrow(CredencialesInvalidasException::new);

        String token = jwtService.generarToken(usuario);

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.expirationSeconds(),
                usuario.getId(),
                usuario.getNombreCompleto(),
                usuario.getCorreo(),
                usuario.getRol().getNombre(),
                aUsuarioResponse(usuario));
    }

    public static String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase();
    }

    public UsuarioResponse aUsuarioResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombreCompleto(),
                usuario.getCorreo(),
                usuario.getRol().getNombre(),
                usuario.getEstado().name().toLowerCase(),
                usuario.getTelefono(),
                usuario.getDocumentoIdentidad(),
                usuario.getFotoPerfilUrl(),
                usuario.getCreadoEn(),
                usuario.getSuspendidoHasta(),
                usuario.getMotivoEstado(),
                alcance.permisosDe(usuario).stream().map(p -> p.valor()).toList());
    }
}
