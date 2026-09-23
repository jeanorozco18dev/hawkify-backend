package com.hawkify.reserva;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.hawkify.catalogo.Producto;
import com.hawkify.parametro.ParametroService;
import com.hawkify.resena.Calificacion;
import com.hawkify.resena.CalificacionRepository;
import com.hawkify.resena.Resena;
import com.hawkify.resena.ResenaRepository;
import com.hawkify.reserva.ReservaDtos.CalificacionRef;
import com.hawkify.reserva.ReservaDtos.DevolucionDto;
import com.hawkify.reserva.ReservaDtos.PagoDto;
import com.hawkify.reserva.ReservaDtos.PersonaRef;
import com.hawkify.reserva.ReservaDtos.ProductoRef;
import com.hawkify.reserva.ReservaDtos.ResenaRef;
import com.hawkify.reserva.ReservaDtos.ReservaResponse;
import com.hawkify.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/** Arma ReservaResponse cargando pagos, devoluciones y calificaciones en lote (sin N+1). */
@Component
@RequiredArgsConstructor
public class ReservaMapper {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final PagoSimuladoRepository pagoRepository;
    private final DevolucionRepository devolucionRepository;
    private final CalificacionRepository calificacionRepository;
    private final ResenaRepository resenaRepository;
    private final ParametroService parametros;

    public ReservaResponse uno(Reserva r, Usuario solicitante) {
        return lista(List.of(r), solicitante).getFirst();
    }

    public List<ReservaResponse> lista(List<Reserva> reservas, Usuario solicitante) {
        if (reservas.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = reservas.stream().map(Reserva::getId).toList();
        Map<UUID, PagoSimulado> pagos = pagoRepository.findByReservaIdIn(ids).stream()
                .collect(Collectors.toMap(p -> p.getReserva().getId(), Function.identity()));
        Map<UUID, Devolucion> devoluciones = devolucionRepository.findByReservaIdIn(ids).stream()
                .collect(Collectors.toMap(d -> d.getReserva().getId(), Function.identity()));
        List<Calificacion> califs = calificacionRepository.findByReservaIdIn(ids);
        Map<UUID, Calificacion> calificaciones = califs.stream()
                .collect(Collectors.toMap(c -> c.getReserva().getId(), Function.identity()));
        Map<UUID, Resena> resenas = califs.isEmpty() ? Map.of()
                : resenaRepository.findByCalificacionIdIn(califs.stream().map(Calificacion::getId).toList()).stream()
                        .collect(Collectors.toMap(x -> x.getCalificacion().getId(), Function.identity()));

        int minutosPago = parametros.entero(ParametroService.RESERVA_MINUTOS_PAGO);
        int horasCancelacion = parametros.entero(ParametroService.CANCELACION_HORAS);

        return reservas.stream().map(r -> {
            Producto p = r.getProducto();
            PagoSimulado pago = pagos.get(r.getId());
            Devolucion dev = devoluciones.get(r.getId());
            Calificacion cal = calificaciones.get(r.getId());
            Resena res = cal == null ? null : resenas.get(cal.getId());

            boolean esArrendatario = solicitante != null && solicitante.getId().equals(r.getUsuario().getId());
            boolean verContacto = solicitante != null && (solicitante.esStaff() || esArrendatario
                    || solicitante.getId().equals(p.getPropietario().getId()));

            return new ReservaResponse(
                    r.getId(), r.getCodigo(),
                    new ProductoRef(p.getId(), p.getCodigo(), p.getNombre(), p.getImagenPrincipal()),
                    r.getUnidad().getCodigoInterno(),
                    r.getFechaInicio(), r.getFechaFin(), r.getDias(),
                    r.getTarifaDiaSnapshot(), r.getSubtotal(), r.getCostoEnvio(), r.getTotal(),
                    r.getModalidadEntrega(), r.getDireccionEntrega(), r.getEstado(), r.getMotivoCancelacion(),
                    r.getCreadoEn(), r.getCanceladoEn(),
                    r.getEstado() == EstadoReserva.PENDIENTE_PAGO ? r.getCreadoEn().plusMinutes(minutosPago) : null,
                    pago == null ? null : new PagoDto(pago.getMetodo(), pago.getEstado(), pago.getMonto(),
                            pago.getReferencia(), pago.getDetalle(), pago.getFechaPago()),
                    dev == null ? null : new DevolucionDto(dev.getEstadoEquipo(), dev.getObservaciones(),
                            dev.getFechaRealDevolucion(), dev.getRegistradoPor().getNombreCompleto()),
                    cal == null ? null : new CalificacionRef(cal.getId(), cal.getEstrellas(), cal.getCreadoEn(),
                            res == null ? null : new ResenaRef(res.getId(),
                                    res.getEstadoModeracion() == com.hawkify.resena.EstadoModeracion.ELIMINADA
                                            ? null : res.getTexto(),
                                    res.getEstadoModeracion())),
                    verContacto ? persona(r.getUsuario()) : null,
                    verContacto ? persona(p.getPropietario()) : null,
                    esArrendatario && puedeCancelarUsuario(r, horasCancelacion),
                    esArrendatario && r.getEstado() == EstadoReserva.FINALIZADA && cal == null);
        }).toList();
    }

    /** RF-24: el usuario cancela dentro de los plazos de la politica. */
    public static boolean puedeCancelarUsuario(Reserva r, int horasMinimas) {
        if (r.getEstado() == EstadoReserva.PENDIENTE_PAGO) {
            return true;
        }
        if (r.getEstado() != EstadoReserva.CONFIRMADA) {
            return false;
        }
        OffsetDateTime inicio = r.getFechaInicio().atStartOfDay(ZONA).toOffsetDateTime();
        return OffsetDateTime.now(ZONA).isBefore(inicio.minusHours(horasMinimas));
    }

    private static PersonaRef persona(Usuario u) {
        return new PersonaRef(u.getId(), u.getNombreCompleto(), u.getCorreo(), u.getTelefono());
    }
}
