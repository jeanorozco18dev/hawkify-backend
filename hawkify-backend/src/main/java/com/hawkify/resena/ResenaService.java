package com.hawkify.resena;

import static com.hawkify.common.Formatos.limpiar;
import static com.hawkify.common.Formatos.mapa;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.admin.AlcanceService;
import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.catalogo.Producto;
import com.hawkify.catalogo.ProductoRepository;
import com.hawkify.common.ApiException;
import com.hawkify.common.PaginaResponse;
import com.hawkify.notificacion.NotificacionService;
import com.hawkify.notificacion.TipoNotificacion;
import com.hawkify.resena.ResenaDtos.CalificacionHistorial;
import com.hawkify.resena.ResenaDtos.CalificarRequest;
import com.hawkify.resena.ResenaDtos.MiCalificacion;
import com.hawkify.resena.ResenaDtos.ModeracionHistorial;
import com.hawkify.resena.ResenaDtos.PersonaRef;
import com.hawkify.resena.ResenaDtos.ProductoRef;
import com.hawkify.resena.ResenaDtos.ReputacionProducto;
import com.hawkify.resena.ResenaDtos.ResenaModeracion;
import com.hawkify.resena.ResenaDtos.ResenaPublica;
import com.hawkify.reserva.EstadoReserva;
import com.hawkify.reserva.Reserva;
import com.hawkify.reserva.ReservaRepository;
import com.hawkify.usuario.CodigoPermiso;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** RF-12..RF-18: calificaciones por reserva finalizada, resenas y moderacion. */
@Service
@RequiredArgsConstructor
public class ResenaService {

    private final CalificacionRepository calificacionRepository;
    private final ResenaRepository resenaRepository;
    private final ModeracionResenaRepository moderacionRepository;
    private final ReservaRepository reservaRepository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlcanceService alcance;
    private final NotificacionService notificaciones;
    private final AuditoriaService auditoria;

    // ------------------------------------------------------------------
    // Usuario final
    // ------------------------------------------------------------------

    @Transactional
    public MiCalificacion calificar(Usuario actor, UUID reservaId, CalificarRequest req) {
        Reserva r = reservaRepository.findDetalle(reservaId)
                .orElseThrow(() -> ApiException.noEncontrado("La reserva no existe"));
        if (!r.getUsuario().getId().equals(actor.getId())) {
            throw ApiException.noEncontrado("La reserva no existe");
        }
        if (r.getEstado() != EstadoReserva.FINALIZADA) {
            throw ApiException.reglaNegocio("Podrás calificar cuando la reserva este finalizada (tras la devolución)");
        }
        if (calificacionRepository.findByReservaId(reservaId).isPresent()) {
            throw ApiException.conflicto("Ya calificaste este alquiler; puedes editar tu calificación");
        }

        Calificacion c = new Calificacion();
        c.setReserva(r);
        c.setUsuario(usuarioRepository.getReferenceById(actor.getId()));
        c.setProducto(r.getProducto());
        c.setEstrellas(req.estrellas().shortValue());
        c = calificacionRepository.saveAndFlush(c);

        Resena resena = null;
        String texto = limpiar(req.texto());
        if (texto != null) {
            resena = nuevaResena(c, texto);
        }

        Producto p = r.getProducto();
        if (!p.getPropietario().getId().equals(actor.getId())) {
            notificaciones.notificar(p.getPropietario(), TipoNotificacion.SISTEMA, "producto", p.getId(),
                    actor.getNombrePublico() + " calificó \"" + p.getNombre() + "\" con " + req.estrellas()
                            + (req.estrellas() == 1 ? " estrella." : " estrellas."));
        }
        return aMiCalificacion(c, resena);
    }

    /** RF-16: el autor edita su calificacion y su texto en cualquier momento. */
    @Transactional
    public MiCalificacion editar(Usuario actor, UUID calificacionId, CalificarRequest req) {
        Calificacion c = calificacionPropia(actor, calificacionId);
        c.setEstrellas(req.estrellas().shortValue());

        String texto = limpiar(req.texto());
        Resena resena = resenaRepository.findByCalificacionId(c.getId()).orElse(null);
        if (texto != null) {
            if (resena == null) {
                resena = nuevaResena(c, texto);
            } else if (resena.getEstadoModeracion() == EstadoModeracion.ELIMINADA) {
                throw ApiException.reglaNegocio("Esta reseña fue eliminada y no se puede volver a publicar");
            } else {
                resena.setTexto(texto);
            }
        }
        calificacionRepository.flush();
        return aMiCalificacion(c, resena);
    }

    /** RF-16: borrado logico; la calificacion numerica se conserva para el promedio. */
    @Transactional
    public void eliminarResena(Usuario actor, UUID resenaId) {
        Resena r = resenaRepository.findDetalle(resenaId)
                .orElseThrow(() -> ApiException.noEncontrado("La reseña no existe"));
        if (!r.getCalificacion().getUsuario().getId().equals(actor.getId())) {
            throw ApiException.noEncontrado("La reseña no existe");
        }
        if (r.getEstadoModeracion() == EstadoModeracion.ELIMINADA) {
            return;
        }
        r.setEstadoModeracion(EstadoModeracion.ELIMINADA);
        r.setEliminadoEn(OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<MiCalificacion> misCalificaciones(Usuario usuario) {
        List<Calificacion> califs = calificacionRepository.deUsuario(usuario.getId());
        Map<UUID, Resena> resenas = resenasPorCalificacion(califs);
        return califs.stream().map(c -> aMiCalificacion(c, resenas.get(c.getId()))).toList();
    }

    /** RF-13 + RF-15: reputacion publica de la ficha. Solo muestra texto de resenas visibles. */
    @Transactional(readOnly = true)
    public ReputacionProducto reputacion(UUID productoId, int pagina, int tamano) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> ApiException.noEncontrado("La herramienta no existe"));

        Map<Integer, Long> distribucion = new LinkedHashMap<>();
        for (int i = 5; i >= 1; i--) {
            distribucion.put(i, 0L);
        }
        calificacionRepository.distribucion(productoId)
                .forEach(f -> distribucion.put(((Number) f[0]).intValue(), ((Number) f[1]).longValue()));

        Page<Calificacion> page = calificacionRepository.deProducto(productoId, PageRequest.of(pagina, tamano));
        Map<UUID, Resena> resenas = resenasPorCalificacion(page.getContent());

        List<ResenaPublica> items = page.getContent().stream().map(c -> {
            Resena r = resenas.get(c.getId());
            boolean visible = r != null && r.getEstadoModeracion() == EstadoModeracion.VISIBLE;
            return new ResenaPublica(c.getId(), c.getUsuario().getNombrePublico(), c.getEstrellas(),
                    visible ? r.getTexto() : null,
                    visible && r.getActualizadoEn().isAfter(r.getCreadoEn().plusMinutes(1)),
                    c.getCreadoEn());
        }).toList();

        return new ReputacionProducto(p.getCalificacionPromedio(), p.getTotalCalificaciones(), distribucion,
                items, pagina, page.getTotalPages());
    }

    // ------------------------------------------------------------------
    // Staff
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PaginaResponse<ResenaModeracion> bandeja(Usuario staff, EstadoModeracion estado, int pagina, int tamano) {
        boolean puedeModerar = alcance.permisosDe(staff).contains(CodigoPermiso.RESENAS_MODERAR);
        Page<Resena> page = resenaRepository.bandeja(staff.esSuperadmin(), staff.getId(), estado,
                PageRequest.of(pagina, tamano));
        return PaginaResponse.de(page, r -> aModeracion(r, staff, puedeModerar));
    }

    @Transactional
    public ResenaModeracion moderar(Usuario staff, UUID resenaId, AccionModeracion accion, String motivo) {
        alcance.exigir(staff, CodigoPermiso.RESENAS_MODERAR);
        Resena r = resenaRepository.findDetalle(resenaId)
                .orElseThrow(() -> ApiException.noEncontrado("La reseña no existe"));
        Producto p = r.getCalificacion().getProducto();
        if (!alcance.gestiona(staff, p)) {
            throw ApiException.prohibido("Solo puedes moderar reseñas de herramientas bajo tu gestión");
        }
        String m = limpiar(motivo);
        EstadoModeracion anterior = r.getEstadoModeracion();

        switch (accion) {
            case OCULTAR -> {
                if (anterior != EstadoModeracion.VISIBLE) {
                    throw ApiException.reglaNegocio("Solo se ocultan reseñas visibles");
                }
                exigirMotivo(m);
                r.setEstadoModeracion(EstadoModeracion.OCULTA);
            }
            case ELIMINAR -> {
                if (anterior == EstadoModeracion.ELIMINADA) {
                    throw ApiException.reglaNegocio("La reseña ya está eliminada");
                }
                exigirMotivo(m);
                r.setEstadoModeracion(EstadoModeracion.ELIMINADA);
                r.setEliminadoEn(OffsetDateTime.now());
            }
            case REVERTIR -> {
                // RF-18: solo el superadmin revierte decisiones de moderacion de un administrador.
                if (!staff.esSuperadmin()) {
                    throw ApiException.prohibido("Solo el superadministrador puede revertir una moderación");
                }
                boolean fueModerada = moderacionRepository.findByResenaIdOrderByFechaDesc(r.getId()).stream()
                        .findFirst()
                        .map(x -> x.getAccion() != AccionModeracion.REVERTIR)
                        .orElse(false);
                if (anterior == EstadoModeracion.VISIBLE || !fueModerada) {
                    throw ApiException.reglaNegocio("No hay una decision de moderación que revertir");
                }
                r.setEstadoModeracion(EstadoModeracion.VISIBLE);
                r.setEliminadoEn(null);
            }
        }

        ModeracionResena registro = new ModeracionResena();
        registro.setResena(r);
        registro.setModerador(usuarioRepository.getReferenceById(staff.getId()));
        registro.setAccion(accion);
        registro.setMotivo(m);
        moderacionRepository.save(registro);

        String aviso = switch (accion) {
            case OCULTAR -> "Tu reseña de \"" + p.getNombre() + "\" fue ocultada por moderación: " + m;
            case ELIMINAR -> "Tu reseña de \"" + p.getNombre() + "\" fue eliminada por moderación: " + m;
            case REVERTIR -> "Tu reseña de \"" + p.getNombre() + "\" vuelve a estar publicada tras una revisión.";
        };
        notificaciones.notificar(r.getCalificacion().getUsuario(), TipoNotificacion.MODERACION, "producto",
                p.getId(), aviso);
        auditoria.registrar(staff, "resena_" + accion.valor(), "resena", r.getId(),
                mapa("estado", anterior.valor()),
                mapa("estado", r.getEstadoModeracion().valor(), "motivo", m, "producto", p.getNombre()));
        return aModeracion(r, staff, true);
    }

    @Transactional(readOnly = true)
    public List<ModeracionHistorial> historial(Usuario staff, UUID resenaId) {
        Resena r = resenaRepository.findDetalle(resenaId)
                .orElseThrow(() -> ApiException.noEncontrado("La reseña no existe"));
        if (!alcance.gestiona(staff, r.getCalificacion().getProducto())) {
            throw ApiException.prohibido("Esta reseña no pertenece a tu inventario");
        }
        return moderacionRepository.findByResenaIdOrderByFechaDesc(resenaId).stream()
                .map(m -> new ModeracionHistorial(m.getId(), m.getAccion(), m.getMotivo(),
                        m.getModerador().getNombreCompleto(), m.getFecha()))
                .toList();
    }

    /** RF-14: historial de calificaciones del inventario para detectar equipos con bajo desempeno. */
    @Transactional(readOnly = true)
    public PaginaResponse<CalificacionHistorial> historialCalificaciones(Usuario staff, Integer maxEstrellas,
                                                                        int pagina, int tamano) {
        Page<Calificacion> page = calificacionRepository.historial(staff.esSuperadmin(), staff.getId(),
                maxEstrellas == null ? null : maxEstrellas.shortValue(), PageRequest.of(pagina, tamano));
        return PaginaResponse.de(page, c -> new CalificacionHistorial(c.getId(),
                new ProductoRef(c.getProducto().getId(), c.getProducto().getNombre(), null),
                new PersonaRef(c.getUsuario().getId(), c.getUsuario().getNombreCompleto(), c.getUsuario().getCorreo()),
                c.getEstrellas(), c.getCreadoEn()));
    }

    // ------------------------------------------------------------------

    private Resena nuevaResena(Calificacion c, String texto) {
        if (texto.length() < 10) {
            throw ApiException.invalido("La reseña debe tener al menos 10 caracteres para ser util a otros");
        }
        Resena r = new Resena();
        r.setCalificacion(c);
        r.setTexto(texto);
        return resenaRepository.save(r);
    }

    private Calificacion calificacionPropia(Usuario actor, UUID id) {
        Calificacion c = calificacionRepository.findDetalle(id)
                .orElseThrow(() -> ApiException.noEncontrado("La calificación no existe"));
        if (!c.getUsuario().getId().equals(actor.getId())) {
            throw ApiException.noEncontrado("La calificación no existe");
        }
        return c;
    }

    private Map<UUID, Resena> resenasPorCalificacion(List<Calificacion> califs) {
        if (califs.isEmpty()) {
            return Map.of();
        }
        return resenaRepository.findByCalificacionIdIn(califs.stream().map(Calificacion::getId).toList()).stream()
                .collect(Collectors.toMap(r -> r.getCalificacion().getId(), Function.identity()));
    }

    private MiCalificacion aMiCalificacion(Calificacion c, Resena r) {
        Producto p = c.getProducto();
        boolean eliminada = r != null && r.getEstadoModeracion() == EstadoModeracion.ELIMINADA;
        return new MiCalificacion(c.getId(), c.getReserva().getId(),
                new ProductoRef(p.getId(), p.getNombre(), p.getImagenPrincipal()), c.getEstrellas(),
                r == null ? null : r.getId(), r == null || eliminada ? null : r.getTexto(),
                r == null ? null : r.getEstadoModeracion(), c.getCreadoEn());
    }

    private ResenaModeracion aModeracion(Resena r, Usuario staff, boolean puedeModerar) {
        Calificacion c = r.getCalificacion();
        Producto p = c.getProducto();
        return new ResenaModeracion(r.getId(), c.getId(),
                new ProductoRef(p.getId(), p.getNombre(), null),
                new PersonaRef(c.getUsuario().getId(), c.getUsuario().getNombreCompleto(), c.getUsuario().getCorreo()),
                c.getEstrellas(), r.getTexto(), r.getEstadoModeracion(), r.getCreadoEn(), r.getEliminadoEn(),
                puedeModerar && alcance.gestiona(staff, p),
                staff.esSuperadmin() && r.getEstadoModeracion() != EstadoModeracion.VISIBLE);
    }

    private static void exigirMotivo(String m) {
        if (m == null || m.length() < 5) {
            throw ApiException.invalido("Indica el motivo de la moderación (mínimo 5 caracteres); el autor lo verá");
        }
    }
}
