package com.hawkify.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

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
                .nombreCompleto(request.nombreCompleto().trim())
                .correo(correo)
                .passwordHash(passwordEncoder.encode(request.password()))
                .rol(rolUsuarioFinal)
                .estado(EstadoUsuario.ACTIVO)
                .correoVerificado(false)
                .build();

        usuario = usuarioRepository.save(usuario);

        return aUsuarioResponse(usuario);
    }

    public AuthResponse login(LoginRequest request) {
        String correo = normalizarCorreo(request.correo());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(correo, request.password()));
        } catch (BadCredentialsException e) {
            throw new CredencialesInvalidasException();
        } catch (DisabledException e) {
            throw new CuentaNoDisponibleException("La cuenta esta desactivada");
        } catch (LockedException e) {
            throw new CuentaNoDisponibleException("La cuenta esta suspendida temporalmente");
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
                usuario.getRol().getNombre());
    }

    private String normalizarCorreo(String correo) {
        return correo.trim().toLowerCase();
    }

    public UsuarioResponse aUsuarioResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombreCompleto(),
                usuario.getCorreo(),
                usuario.getRol().getNombre(),
                usuario.getEstado().name().toLowerCase());
    }
}
