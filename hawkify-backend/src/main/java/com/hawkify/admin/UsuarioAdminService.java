package com.hawkify.admin;

import static com.hawkify.common.Formatos.limpiar;
import static com.hawkify.common.Formatos.mapa;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.auth.AuthService;
import com.hawkify.auth.CorreoYaRegistradoException;
import com.hawkify.auth.UsuarioResponse;
import com.hawkify.common.ApiException;
import com.hawkify.common.PaginaResponse;
import com.hawkify.notificacion.NotificacionService;
import com.hawkify.notificacion.TipoNotificacion;
import com.hawkify.usuario.CodigoPermiso;
import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.Permiso;
import com.hawkify.usuario.PermisoRepository;
import com.hawkify.usuario.Rol;
import com.hawkify.usuario.RolNombre;
import com.hawkify.usuario.RolRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/**
 * RF-04: el administrador gestiona usuarios finales.
 * RF-05: el superadmin crea, edita y revoca administradores y define su alcance.
 */
@Service
@RequiredArgsConstructor
public class UsuarioAdminService {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AlcanceService alcance;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;

    public record CrearUsuarioRequest(
            @NotBlank(message = "El nombre completo es obligatorio")
            @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
            String nombreCompleto,
            @NotBlank(message = "El correo es obligatorio")
            @Email(message = "El correo no tiene un formato válido")
            String correo,
            @NotBlank(message = "Asigna una contraseña temporal")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe combinar letras y números")
            String password,
            @Pattern(regexp = "^$|^[0-9+ ()-]{7,20}$", message = "El teléfono no tiene un formato válido")
            String telefono,
            String rol,
            List<CodigoPermiso> permisos) {
    }

    public record EditarUsuarioRequest(
            @NotBlank(message = "El nombre completo es obligatorio")
            @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
            String nombreCompleto,
            @Pattern(regexp = "^$|^[0-9+ ()-]{7,20}$", message = "El teléfono no tiene un formato válido")
            String telefono,
            @Pattern(regexp = "^$|^[0-9A-Za-z.-]{5,20}$", message = "El documento no tiene un formato válido")
            String documentoIdentidad,
            String rol,
            List<CodigoPermiso> permisos) {
    }

    public record EstadoUsuarioRequest(
            @NotNull(message = "Indica el nuevo estado") EstadoUsuario estado,
            @Size(max = 300, message = "El motivo no puede superar 300 caracteres") String motivo,
            OffsetDateTime suspendidoHasta) {
    }

    public record PermisoResponse(String codigo, String descripcion) {
    }

    @Transactional(readOnly = true)
    public PaginaResponse<UsuarioResponse> listar(Usuario staff, String q, String rol, EstadoUsuario estado,
                                                 int pagina, int tamano) {
        alcance.exigir(staff, CodigoPermiso.USUARIOS_GESTIONAR);
        // Un administrador solo ve usuarios finales; el superadmin ve a todos.
        String rolFiltro = staff.esSuperadmin() ? limpiar(rol) : RolNombre.USUARIO_FINAL;
        String like = limpiar(q) == null ? null : "%" + q.trim().toLowerCase() + "%";
        var page = usuarioRepository.buscar(like, rolFiltro, estado,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Order.desc("creadoEn"))));
        return PaginaResponse.de(page, authService::aUsuarioResponse);
    }

    @Transactional(readOnly = true)
    public List<PermisoResponse> catalogoPermisos() {
        return permisoRepository.findAll(Sort.by("id")).stream()
                .map(p -> new PermisoResponse(p.getCodigo(), p.getDescripcion())).toList();
    }

    @Transactional
    public UsuarioResponse crear(Usuario staff, CrearUsuarioRequest req) {
        alcance.exigir(staff, CodigoPermiso.USUARIOS_GESTIONAR);
        String rolNombre = resolverRolPermitido(staff, req.rol());
        String correo = AuthService.normalizarCorreo(req.correo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new CorreoYaRegistradoException(correo);
        }
        Rol rol = rolRepository.findByNombre(rolNombre).orElseThrow();

        Usuario u = Usuario.builder()
                .nombreCompleto(req.nombreCompleto().trim())
                .correo(correo)
                .passwordHash(passwordEncoder.encode(req.password()))
                .telefono(limpiar(req.telefono()))
                .rol(rol)
                .estado(EstadoUsuario.ACTIVO)
                .build();
        u = usuarioRepository.saveAndFlush(u);

        if (RolNombre.ADMINISTRADOR.equals(rolNombre)) {
            asignarPermisos(u, req.permisos() == null ? EnumSet.allOf(CodigoPermiso.class) : Set.copyOf(req.permisos()));
        }
        auditoria.registrar(staff, "usuario_creado", "usuario", u.getId(), null,
                mapa("correo", correo, "rol", rolNombre, "permisos", req.permisos()));
        return authService.aUsuarioResponse(u);
    }

    @Transactional
    public UsuarioResponse editar(Usuario staff, UUID id, EditarUsuarioRequest req) {
        alcance.exigir(staff, CodigoPermiso.USUARIOS_GESTIONAR);
        Usuario u = cargarEditable(staff, id);
        var previos = mapa("nombreCompleto", u.getNombreCompleto(), "rol", u.getNombreRol(),
                "permisos", alcance.permisosDe(u));

        u.setNombreCompleto(req.nombreCompleto().trim());
        u.setTelefono(limpiar(req.telefono()));
        u.setDocumentoIdentidad(limpiar(req.documentoIdentidad()));

        if (staff.esSuperadmin() && limpiar(req.rol()) != null && !req.rol().equals(u.getNombreRol())) {
            String nuevoRol = resolverRolPermitido(staff, req.rol());
            u.setRol(rolRepository.findByNombre(nuevoRol).orElseThrow());
            if (!RolNombre.ADMINISTRADOR.equals(nuevoRol)) {
                permisoRepository.quitarTodos(u.getId());
            }
        }
        if (staff.esSuperadmin() && u.esAdministrador() && req.permisos() != null) {
            asignarPermisos(u, Set.copyOf(req.permisos()));
        }
        usuarioRepository.flush();
        auditoria.registrar(staff, "usuario_editado", "usuario", u.getId(), previos,
                mapa("nombreCompleto", u.getNombreCompleto(), "rol", u.getNombreRol(),
                        "permisos", alcance.permisosDe(u)));
        return authService.aUsuarioResponse(u);
    }

    /** RF-04: activar, suspender (temporal) o desactivar. RF-05: revocar un administrador. */
    @Transactional
    public UsuarioResponse cambiarEstado(Usuario staff, UUID id, EstadoUsuarioRequest req) {
        alcance.exigir(staff, CodigoPermiso.USUARIOS_GESTIONAR);
        Usuario u = cargarEditable(staff, id);
        String motivo = limpiar(req.motivo());
        EstadoUsuario anterior = u.getEstado();

        if (req.estado() != EstadoUsuario.ACTIVO && (motivo == null || motivo.length() < 5)) {
            throw ApiException.invalido("Indica el motivo (mínimo 5 caracteres); el usuario lo verá al intentar entrar");
        }
        if (req.estado() == EstadoUsuario.SUSPENDIDO && req.suspendidoHasta() != null
                && req.suspendidoHasta().isBefore(OffsetDateTime.now())) {
            throw ApiException.invalido("La fecha de fin de la suspensión debe ser futura");
        }

        u.setEstado(req.estado());
        u.setMotivoEstado(req.estado() == EstadoUsuario.ACTIVO ? null : motivo);
        u.setSuspendidoHasta(req.estado() == EstadoUsuario.SUSPENDIDO ? req.suspendidoHasta() : null);

        if (req.estado() == EstadoUsuario.ACTIVO && anterior != EstadoUsuario.ACTIVO) {
            notificaciones.notificar(u, TipoNotificacion.SISTEMA, "usuario", u.getId(),
                    "Tu cuenta fue reactivada. Ya puedes reservar de nuevo.");
        }
        auditoria.registrar(staff, "usuario_estado", "usuario", u.getId(),
                mapa("estado", anterior.name().toLowerCase()),
                mapa("estado", req.estado().name().toLowerCase(), "motivo", motivo,
                        "suspendidoHasta", req.suspendidoHasta()));
        return authService.aUsuarioResponse(u);
    }

    private Usuario cargarEditable(Usuario staff, UUID id) {
        Usuario u = usuarioRepository.findByIdConRol(id)
                .filter(x -> x.getEliminadoEn() == null)
                .orElseThrow(() -> ApiException.noEncontrado("El usuario no existe"));
        if (u.getId().equals(staff.getId())) {
            throw ApiException.reglaNegocio("No puedes modificar tu propia cuenta desde el panel; usa tu perfil");
        }
        if (u.esSuperadmin()) {
            throw ApiException.prohibido("La cuenta de superadministrador no se gestiona desde el panel");
        }
        if (!staff.esSuperadmin() && !RolNombre.USUARIO_FINAL.equals(u.getNombreRol())) {
            throw ApiException.prohibido("Solo el superadministrador gestiona cuentas de administrador");
        }
        return u;
    }

    private String resolverRolPermitido(Usuario staff, String rol) {
        String r = limpiar(rol) == null ? RolNombre.USUARIO_FINAL : rol.trim();
        if (!RolNombre.USUARIO_FINAL.equals(r) && !RolNombre.ADMINISTRADOR.equals(r)) {
            throw ApiException.invalido("Rol no válido: solo 'usuario_final' o 'administrador'");
        }
        if (RolNombre.ADMINISTRADOR.equals(r) && !staff.esSuperadmin()) {
            throw ApiException.prohibido("Solo el superadministrador crea cuentas de administrador");
        }
        return r;
    }

    private void asignarPermisos(Usuario admin, Set<CodigoPermiso> permisos) {
        permisoRepository.quitarTodos(admin.getId());
        List<String> codigos = permisos.stream().map(CodigoPermiso::valor).toList();
        for (Permiso p : permisoRepository.findByCodigoIn(codigos)) {
            permisoRepository.asignar(admin.getId(), p.getId());
        }
    }
}
