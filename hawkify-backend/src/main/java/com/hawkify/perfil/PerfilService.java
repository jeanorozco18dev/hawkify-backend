package com.hawkify.perfil;

import static com.hawkify.common.Formatos.limpiar;
import static com.hawkify.common.Formatos.mapa;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.auth.AuthService;
import com.hawkify.auth.UsuarioResponse;
import com.hawkify.catalogo.EstadoPublicacion;
import com.hawkify.catalogo.EstadoUnidad;
import com.hawkify.catalogo.ProductoRepository;
import com.hawkify.common.ApiException;
import com.hawkify.notificacion.NotificacionRepository;
import com.hawkify.reserva.EstadoReserva;
import com.hawkify.reserva.ReservaRepository;
import com.hawkify.reserva.ReservaService;
import com.hawkify.resena.ResenaService;
import com.hawkify.usuario.Direccion;
import com.hawkify.usuario.DireccionRepository;
import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.TokenRecuperacionRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;
import com.hawkify.wishlist.ListaDeseosItemRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** RF-03 (perfil y direcciones) y derechos de Habeas Data sobre los datos propios. */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final DireccionRepository direccionRepository;
    private final ReservaRepository reservaRepository;
    private final ProductoRepository productoRepository;
    private final ListaDeseosItemRepository listaDeseosRepository;
    private final NotificacionRepository notificacionRepository;
    private final TokenRecuperacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final ReservaService reservaService;
    private final ResenaService resenaService;
    private final AuditoriaService auditoria;

    public record PerfilRequest(
            @NotBlank(message = "El nombre completo es obligatorio")
            @Size(min = 3, max = 150, message = "El nombre debe tener entre 3 y 150 caracteres")
            String nombreCompleto,
            @Pattern(regexp = "^$|^[0-9+ ()-]{7,20}$", message = "El teléfono solo admite números, espacios y + ( ) -")
            String telefono,
            @Pattern(regexp = "^$|^[0-9A-Za-z.-]{5,20}$", message = "El documento debe tener entre 5 y 20 caracteres alfanuméricos")
            String documentoIdentidad,
            @Size(max = 500, message = "La URL de la foto es demasiado larga")
            String fotoPerfilUrl) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Escribe tu contraseña actual") String actual,
            @NotBlank(message = "Escribe la nueva contraseña")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe combinar letras y números")
            String nueva) {
    }

    public record DireccionRequest(
            @Size(max = 50, message = "La etiqueta no puede superar 50 caracteres") String etiqueta,
            @NotBlank(message = "La dirección es obligatoria")
            @Size(max = 200, message = "La dirección no puede superar 200 caracteres") String linea1,
            @Size(max = 200, message = "El complemento no puede superar 200 caracteres") String linea2,
            @NotBlank(message = "La ciudad es obligatoria") @Size(max = 100) String ciudad,
            @NotBlank(message = "El departamento es obligatorio") @Size(max = 100) String departamento,
            @Size(max = 20) String codigoPostal,
            Boolean predeterminada) {
    }

    public record DireccionResponse(UUID id, String etiqueta, String linea1, String linea2, String ciudad,
                                    String departamento, String codigoPostal, boolean predeterminada,
                                    String texto) {
        static DireccionResponse de(Direccion d) {
            return new DireccionResponse(d.getId(), d.getEtiqueta(), d.getLinea1(), d.getLinea2(), d.getCiudad(),
                    d.getDepartamento(), d.getCodigoPostal(), d.isPredeterminada(), d.comoTexto());
        }
    }

    public record EliminarCuentaRequest(@NotBlank(message = "Confirma con tu contraseña") String password) {
    }

    @Transactional(readOnly = true)
    public UsuarioResponse perfil(Usuario actor) {
        return authService.aUsuarioResponse(cargar(actor));
    }

    @Transactional
    public UsuarioResponse actualizar(Usuario actor, PerfilRequest req) {
        Usuario u = cargar(actor);
        var previos = mapa("nombreCompleto", u.getNombreCompleto(), "telefono", u.getTelefono(),
                "documentoIdentidad", u.getDocumentoIdentidad());
        u.setNombreCompleto(req.nombreCompleto().trim().replaceAll("\\s+", " "));
        u.setTelefono(limpiar(req.telefono()));
        u.setDocumentoIdentidad(limpiar(req.documentoIdentidad()));
        u.setFotoPerfilUrl(limpiar(req.fotoPerfilUrl()));
        auditoria.registrar(u, "perfil_actualizado", "usuario", u.getId(), previos,
                mapa("nombreCompleto", u.getNombreCompleto(), "telefono", u.getTelefono(),
                        "documentoIdentidad", u.getDocumentoIdentidad()));
        return authService.aUsuarioResponse(u);
    }

    @Transactional
    public void cambiarPassword(Usuario actor, PasswordRequest req) {
        Usuario u = cargar(actor);
        if (!passwordEncoder.matches(req.actual(), u.getPasswordHash())) {
            throw ApiException.invalido("La contraseña actual no es correcta");
        }
        if (passwordEncoder.matches(req.nueva(), u.getPasswordHash())) {
            throw ApiException.invalido("La nueva contraseña debe ser distinta a la actual");
        }
        u.setPasswordHash(passwordEncoder.encode(req.nueva()));
        auditoria.registrar(u, "password_cambiada", "usuario", u.getId(), null, null);
    }

    @Transactional(readOnly = true)
    public List<DireccionResponse> direcciones(Usuario actor) {
        return direccionRepository.findByUsuarioIdOrderByPredeterminadaDescEtiquetaAsc(actor.getId()).stream()
                .map(DireccionResponse::de).toList();
    }

    @Transactional
    public DireccionResponse crearDireccion(Usuario actor, DireccionRequest req) {
        if (direccionRepository.countByUsuarioId(actor.getId()) >= 10) {
            throw ApiException.reglaNegocio("Puedes guardar máximo 10 direcciones");
        }
        Direccion d = new Direccion();
        d.setUsuario(usuarioRepository.getReferenceById(actor.getId()));
        boolean primera = direccionRepository.countByUsuarioId(actor.getId()) == 0;
        aplicar(d, req, actor.getId(), primera);
        return DireccionResponse.de(direccionRepository.save(d));
    }

    @Transactional
    public DireccionResponse actualizarDireccion(Usuario actor, UUID id, DireccionRequest req) {
        Direccion d = direccionRepository.findByIdAndUsuarioId(id, actor.getId())
                .orElseThrow(() -> ApiException.noEncontrado("La dirección no existe"));
        aplicar(d, req, actor.getId(), false);
        return DireccionResponse.de(d);
    }

    @Transactional
    public void eliminarDireccion(Usuario actor, UUID id) {
        Direccion d = direccionRepository.findByIdAndUsuarioId(id, actor.getId())
                .orElseThrow(() -> ApiException.noEncontrado("La dirección no existe"));
        direccionRepository.delete(d);
    }

    /** Habeas Data: acceso y consulta. Devuelve todo lo que Hawkify guarda del titular. */
    @Transactional
    public Map<String, Object> exportar(Usuario actor) {
        Usuario u = cargar(actor);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("generadoEn", OffsetDateTime.now());
        datos.put("titular", authService.aUsuarioResponse(u));
        datos.put("aceptoPoliticaDatosEn", u.getAceptoPoliticaEn());
        datos.put("direcciones", direcciones(u));
        datos.put("reservas", reservaService.misReservas(u));
        datos.put("calificaciones", resenaService.misCalificaciones(u));
        datos.put("herramientasPublicadas", productoRepository.findByPropietarioIdOrderByCreadoEnDesc(u.getId())
                .stream().map(p -> Map.of("id", p.getId(), "nombre", p.getNombre(),
                        "estado", p.getEstadoPublicacion().valor())).toList());
        auditoria.registrar(u, "datos_exportados", "usuario", u.getId(), null, null);
        return datos;
    }

    /**
     * Habeas Data: supresion. Se anonimiza en lugar de borrar para no romper el
     * historial transaccional (reservas, reportes) que el negocio debe conservar.
     */
    @Transactional
    public void eliminarCuenta(Usuario actor, EliminarCuentaRequest req) {
        Usuario u = cargar(actor);
        if (u.esSuperadmin()) {
            throw ApiException.reglaNegocio("La cuenta de superadministrador no se puede eliminar desde el perfil");
        }
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw ApiException.invalido("La contraseña no es correcta");
        }
        var activas = List.of(EstadoReserva.PENDIENTE_PAGO, EstadoReserva.CONFIRMADA, EstadoReserva.EN_CURSO,
                EstadoReserva.CON_INCIDENCIA);
        if (reservaRepository.existsByUsuarioIdAndEstadoIn(u.getId(), activas)) {
            throw ApiException.conflicto("Tienes reservas activas. Cancélalas o espera a que finalicen para eliminar tu cuenta.");
        }
        productoRepository.findByPropietarioIdOrderByCreadoEnDesc(u.getId()).forEach(p -> {
            if (reservaRepository.existenDeProductoEnEstados(p.getId(), activas)) {
                throw ApiException.conflicto("La herramienta \"" + p.getNombre()
                        + "\" tiene reservas activas. Espera a que terminen para eliminar tu cuenta.");
            }
            p.setEstadoPublicacion(EstadoPublicacion.RETIRADO);
            p.getUnidades().forEach(x -> x.setEstado(EstadoUnidad.RETIRADA));
        });

        String correoOriginal = u.getCorreo();
        u.setNombreCompleto("Usuario eliminado");
        u.setCorreo("eliminado-" + u.getId() + "@hawkify.invalid");
        u.setTelefono(null);
        u.setDocumentoIdentidad(null);
        u.setFotoPerfilUrl(null);
        u.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        u.setEstado(EstadoUsuario.DESACTIVADO);
        u.setMotivoEstado("Supresión solicitada por el titular");
        u.setEliminadoEn(OffsetDateTime.now());

        direccionRepository.borrarDeUsuario(u.getId());
        listaDeseosRepository.borrarDeUsuario(u.getId());
        notificacionRepository.borrarDeUsuario(u.getId());
        tokenRepository.borrarDeUsuario(u.getId());

        auditoria.registrar(u, "cuenta_suprimida", "usuario", u.getId(),
                mapa("dominioCorreo", correoOriginal.substring(correoOriginal.indexOf('@') + 1)), null);
    }

    private Usuario cargar(Usuario actor) {
        return usuarioRepository.findByIdConRol(actor.getId())
                .orElseThrow(() -> ApiException.noEncontrado("La cuenta no existe"));
    }

    private void aplicar(Direccion d, DireccionRequest req, UUID usuarioId, boolean forzarPredeterminada) {
        d.setEtiqueta(limpiar(req.etiqueta()));
        d.setLinea1(req.linea1().trim());
        d.setLinea2(limpiar(req.linea2()));
        d.setCiudad(req.ciudad().trim());
        d.setDepartamento(req.departamento().trim());
        d.setCodigoPostal(limpiar(req.codigoPostal()));
        if (forzarPredeterminada || Boolean.TRUE.equals(req.predeterminada())) {
            if (!d.isPredeterminada()) {
                direccionRepository.quitarPredeterminada(usuarioId);
            }
            d.setPredeterminada(true);
        } else if (Boolean.FALSE.equals(req.predeterminada())) {
            d.setPredeterminada(false);
        }
    }
}
