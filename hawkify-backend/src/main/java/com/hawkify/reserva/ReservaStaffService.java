package com.hawkify.reserva;

import static com.hawkify.common.Formatos.limpiar;
import static com.hawkify.common.Formatos.mapa;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.query.criteria.JpaExpression;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.admin.AlcanceService;
import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.catalogo.EstadoUnidad;
import com.hawkify.catalogo.Producto;
import com.hawkify.common.ApiException;
import com.hawkify.common.PaginaResponse;
import com.hawkify.notificacion.NotificacionService;
import com.hawkify.notificacion.TipoNotificacion;
import com.hawkify.reserva.ReservaDtos.DevolucionRequest;
import com.hawkify.reserva.ReservaDtos.ReservaResponse;
import com.hawkify.usuario.CodigoPermiso;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * Operacion de reservas por el staff: calendario, cambios de estado y
 * devoluciones (RF-25, RF-26, RF-27). El admin solo ve su inventario.
 */
@Service
@RequiredArgsConstructor
public class ReservaStaffService {

    private final ReservaRepository reservaRepository;
    private final DevolucionRepository devolucionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReservaService reservaService;
    private final ReservaMapper mapper;
    private final AlcanceService alcance;
    private final NotificacionService notificaciones;
    private final AuditoriaService auditoria;

    public record Filtros(EstadoReserva estado, UUID productoId, String q, LocalDate desde, LocalDate hasta,
                          int pagina, int tamano) {
    }

    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> listar(Usuario staff, Filtros f) {
        Specification<Reserva> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            var unidad = root.join("unidad");
            var producto = unidad.join("producto");
            var usuario = root.join("usuario");
            if (!staff.esSuperadmin()) {
                ps.add(cb.or(
                        cb.equal(producto.get("administradorRevisor").get("id"), staff.getId()),
                        cb.equal(producto.get("propietario").get("id"), staff.getId())));
            }
            if (f.estado() != null) {
                ps.add(cb.equal(root.get("estado"), f.estado()));
            }
            if (f.productoId() != null) {
                ps.add(cb.equal(producto.get("id"), f.productoId()));
            }
            if (f.desde() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("fechaFin"), f.desde()));
            }
            if (f.hasta() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("fechaInicio"), f.hasta()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String texto = f.q().trim().toLowerCase();
                String like = "%" + texto + "%";
                // El codigo visible R-XXXXXXXX son los primeros 8 caracteres del UUID.
                var idComoTexto = ((JpaExpression<?>) root.get("id")).cast(String.class);
                ps.add(cb.or(
                        cb.like(cb.lower(producto.get("nombre")), like),
                        cb.like(cb.lower(usuario.get("nombreCompleto")), like),
                        cb.like(cb.lower(usuario.get("correo")), like),
                        cb.like(idComoTexto, texto.replaceFirst("^r-", "") + "%")));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        Page<Reserva> page = reservaRepository.findAll(spec,
                PageRequest.of(f.pagina(), f.tamano(), Sort.by(Sort.Order.desc("fechaInicio"))));
        return PaginaResponse.de(mapper.lista(page.getContent(), staff), f.pagina(), f.tamano(),
                page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ReservaResponse detalle(Usuario staff, UUID id) {
        return mapper.uno(cargarGestionada(staff, id), staff);
    }

    /** RF-25: actualizacion manual del estado (entrega, incidencia, cierre, cancelacion). */
    @Transactional
    public ReservaResponse cambiarEstado(Usuario staff, UUID id, EstadoReserva destino, String motivo) {
        alcance.exigir(staff, CodigoPermiso.RESERVAS_GESTIONAR);
        Reserva r = cargarGestionada(staff, id);
        EstadoReserva actual = r.getEstado();

        if (!actual.puedeTransicionarA(destino)) {
            throw ApiException.reglaNegocio("No se puede pasar una reserva de '" + actual.valor()
                    + "' a '" + destino.valor() + "'");
        }
        if (destino == EstadoReserva.FINALIZADA && !devolucionRepository.existsByReservaId(r.getId())) {
            throw ApiException.reglaNegocio("Registra primero la devolución del equipo para finalizar la reserva");
        }
        if (destino == EstadoReserva.CONFIRMADA) {
            throw ApiException.reglaNegocio("Una reserva solo se confirma cuando el cliente completa el pago");
        }
        String m = limpiar(motivo);
        if ((destino == EstadoReserva.CANCELADA || destino == EstadoReserva.CON_INCIDENCIA) && m == null) {
            throw ApiException.invalido("Escribe el motivo; quedará en el historial y lo verá el cliente");
        }

        if (destino == EstadoReserva.CANCELADA) {
            reservaService.aplicarCancelacion(r, m);
        } else {
            r.setEstado(destino);
        }

        String mensaje = switch (destino) {
            case EN_CURSO -> "Entregamos \"" + r.getProducto().getNombre() + "\" (reserva " + r.getCodigo()
                    + "). Recuerda devolverla el " + com.hawkify.common.Formatos.fecha(r.getFechaFin()) + ".";
            case CON_INCIDENCIA -> "Tu reserva " + r.getCodigo() + " tiene una incidencia registrada: " + m;
            case FINALIZADA -> "Cerramos la reserva " + r.getCodigo() + ". Ya puedes calificar la herramienta.";
            case CANCELADA -> "Cancelamos tu reserva " + r.getCodigo() + ": " + m
                    + ". Si ya habías pagado, el reembolso queda registrado.";
            default -> null;
        };
        if (mensaje != null) {
            notificaciones.notificar(r.getUsuario(), TipoNotificacion.RESERVA, "reserva", r.getId(), mensaje);
        }
        auditoria.registrar(staff, "reserva_estado", "reserva", r.getId(),
                mapa("estado", actual.valor()), mapa("estado", destino.valor(), "motivo", m));
        return mapper.uno(r, staff);
    }

    /** RF-26: acta de devolucion con estado del equipo y novedades. */
    @Transactional
    public ReservaResponse registrarDevolucion(Usuario staff, UUID id, DevolucionRequest req) {
        alcance.exigir(staff, CodigoPermiso.RESERVAS_GESTIONAR);
        Reserva r = cargarGestionada(staff, id);
        if (r.getEstado() != EstadoReserva.EN_CURSO && r.getEstado() != EstadoReserva.CON_INCIDENCIA) {
            throw ApiException.reglaNegocio("Solo se registra la devolución de reservas en curso o con incidencia");
        }
        if (devolucionRepository.existsByReservaId(r.getId())) {
            throw ApiException.conflicto("La devolución de esta reserva ya fue registrada");
        }
        String obs = limpiar(req.observaciones());
        if (req.estadoEquipo() != EstadoEquipo.SIN_NOVEDAD && (obs == null || obs.length() < 10)) {
            throw ApiException.invalido("Describe el daño o la novedad encontrada (mínimo 10 caracteres)");
        }

        Devolucion d = new Devolucion();
        d.setReserva(r);
        d.setRegistradoPor(usuarioRepository.getReferenceById(staff.getId()));
        d.setEstadoEquipo(req.estadoEquipo());
        d.setObservaciones(obs);
        devolucionRepository.save(d);

        EstadoReserva anterior = r.getEstado();
        Producto p = r.getProducto();
        String mensaje;
        switch (req.estadoEquipo()) {
            case SIN_NOVEDAD -> {
                r.setEstado(EstadoReserva.FINALIZADA);
                mensaje = "Recibimos \"" + p.getNombre() + "\" sin novedades. Gracias. Ya puedes calificarla.";
            }
            case CON_DANO -> {
                r.setEstado(EstadoReserva.CON_INCIDENCIA);
                r.getUnidad().setEstado(EstadoUnidad.EN_MANTENIMIENTO);
                mensaje = "Registramos la devolución de \"" + p.getNombre() + "\" con danos: " + obs
                        + ". Te contactaremos para acordar la valoración.";
            }
            default -> {
                r.setEstado(EstadoReserva.CON_INCIDENCIA);
                r.getUnidad().setEstado(EstadoUnidad.RETIRADA);
                mensaje = "Registramos la pérdida de \"" + p.getNombre() + "\" (reserva " + r.getCodigo()
                        + "). Te contactaremos para acordar la valoración.";
            }
        }
        notificaciones.notificar(r.getUsuario(), TipoNotificacion.RESERVA, "reserva", r.getId(), mensaje);
        auditoria.registrar(staff, "devolucion_registrada", "reserva", r.getId(),
                mapa("estado", anterior.valor()),
                mapa("estado", r.getEstado().valor(), "estadoEquipo", req.estadoEquipo().valor(),
                        "unidad", r.getUnidad().getCodigoInterno(), "observaciones", obs));
        return mapper.uno(r, staff);
    }

    /** Reservas sobre las herramientas publicadas por un usuario (vista del propietario). */
    @Transactional(readOnly = true)
    public List<ReservaResponse> deMisHerramientas(Usuario propietario) {
        return mapper.lista(reservaRepository.deProductosDePropietario(propietario.getId()), propietario);
    }

    private Reserva cargarGestionada(Usuario staff, UUID id) {
        Reserva r = reservaRepository.findDetalle(id)
                .orElseThrow(() -> ApiException.noEncontrado("La reserva no existe"));
        if (!alcance.gestiona(staff, r.getProducto())) {
            throw ApiException.prohibido("Esta reserva corresponde a una herramienta que no gestionas");
        }
        return r;
    }
}
