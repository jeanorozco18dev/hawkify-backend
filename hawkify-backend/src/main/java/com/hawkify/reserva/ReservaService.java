package com.hawkify.reserva;

import static com.hawkify.common.Formatos.fecha;
import static com.hawkify.common.Formatos.limpiar;
import static com.hawkify.common.Formatos.mapa;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.catalogo.Producto;
import com.hawkify.catalogo.ProductoRepository;
import com.hawkify.catalogo.UnidadProducto;
import com.hawkify.catalogo.UnidadProductoRepository;
import com.hawkify.common.ApiException;
import com.hawkify.notificacion.NotificacionService;
import com.hawkify.notificacion.TipoNotificacion;
import com.hawkify.parametro.ParametroService;
import com.hawkify.reserva.ReservaDtos.Cotizacion;
import com.hawkify.reserva.ReservaDtos.CotizacionRequest;
import com.hawkify.reserva.ReservaDtos.CrearReservaRequest;
import com.hawkify.reserva.ReservaDtos.PagoRequest;
import com.hawkify.reserva.ReservaDtos.ReservaResponse;
import com.hawkify.usuario.Direccion;
import com.hawkify.usuario.DireccionRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** Flujo del arrendatario: cotizar -> reservar -> pagar (simulado) -> cancelar (RF-22..RF-24). */
@Service
@RequiredArgsConstructor
public class ReservaService {

    private static final Logger log = LoggerFactory.getLogger(ReservaService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ReservaRepository reservaRepository;
    private final PagoSimuladoRepository pagoRepository;
    private final ProductoRepository productoRepository;
    private final UnidadProductoRepository unidadRepository;
    private final DireccionRepository direccionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ParametroService parametros;
    private final NotificacionService notificaciones;
    private final AuditoriaService auditoria;
    private final ReservaMapper mapper;

    @Transactional(readOnly = true)
    public Cotizacion cotizar(CotizacionRequest req) {
        Producto p = productoRepository.findById(req.productoId())
                .orElseThrow(() -> ApiException.noEncontrado("La herramienta no existe"));
        ModalidadEntrega modalidad = req.modalidadEntrega() == null ? ModalidadEntrega.RECOGIDA : req.modalidadEntrega();

        String error = validarFechas(req.fechaInicio(), req.fechaFin());
        int dias = (int) ChronoUnit.DAYS.between(req.fechaInicio(), req.fechaFin()) + 1;
        BigDecimal subtotal = p.getTarifaDia().multiply(BigDecimal.valueOf(Math.max(dias, 0)));
        BigDecimal envio = modalidad == ModalidadEntrega.DOMICILIO
                ? parametros.decimal(ParametroService.ENVIO_TARIFA) : BigDecimal.ZERO;

        int libres = 0;
        String mensaje = error;
        if (mensaje == null && !p.esReservable()) {
            mensaje = "Esta herramienta no está disponible para reservas en este momento";
        }
        if (mensaje == null) {
            libres = unidadRepository.libres(p.getId(), req.fechaInicio(), req.fechaFin()).size();
            if (libres == 0) {
                mensaje = "No hay una unidad libre durante todo ese rango. Prueba moviendo o acortando las fechas.";
            }
        }

        return new Cotizacion(p.getId(), p.getNombre(), req.fechaInicio(), req.fechaFin(), Math.max(dias, 0),
                p.getTarifaDia(), subtotal, envio, subtotal.add(envio), modalidad, mensaje == null, libres,
                mensaje, parametros.entero(ParametroService.CANCELACION_HORAS),
                parametros.texto(ParametroService.CANCELACION_POLITICA));
    }

    @Transactional
    public ReservaResponse crear(Usuario actor, CrearReservaRequest req) {
        if (actor.esSuperadmin()) {
            throw ApiException.prohibido("La cuenta de superadministrador no puede reservar herramientas");
        }
        String errorFechas = validarFechas(req.fechaInicio(), req.fechaFin());
        if (errorFechas != null) {
            throw ApiException.invalido(errorFechas);
        }

        Usuario usuario = usuarioRepository.findById(actor.getId()).orElseThrow();
        completarDatosPersonales(usuario, req);

        // Bloqueo de fila: dos reservas simultaneas del mismo producto se serializan aqui.
        Producto p = productoRepository.findByIdParaReservar(req.productoId())
                .orElseThrow(() -> ApiException.noEncontrado("La herramienta no existe"));
        if (!p.esReservable()) {
            throw ApiException.reglaNegocio("Esta herramienta no está disponible para reservas en este momento");
        }
        if (p.getPropietario().getId().equals(usuario.getId())) {
            throw ApiException.reglaNegocio("No puedes reservar una herramienta que tu mismo publicaste");
        }

        List<UnidadProducto> libres = unidadRepository.libres(p.getId(), req.fechaInicio(), req.fechaFin());
        if (libres.isEmpty()) {
            throw ApiException.conflicto("Alguien acaba de reservar la última unidad para esas fechas. "
                    + "Elige otro rango en el calendario.");
        }

        Reserva r = new Reserva();
        r.setUsuario(usuario);
        r.setUnidad(libres.getFirst());
        r.setFechaInicio(req.fechaInicio());
        r.setFechaFin(req.fechaFin());
        r.setTarifaDiaSnapshot(p.getTarifaDia());
        r.setSubtotal(p.getTarifaDia().multiply(BigDecimal.valueOf(r.getDias())));
        r.setModalidadEntrega(req.modalidadEntrega());
        if (req.modalidadEntrega() == ModalidadEntrega.DOMICILIO) {
            r.setDireccionEntrega(resolverDireccion(usuario, req));
            r.setCostoEnvio(parametros.decimal(ParametroService.ENVIO_TARIFA));
        } else {
            r.setCostoEnvio(BigDecimal.ZERO);
        }
        r.setTotal(r.getSubtotal().add(r.getCostoEnvio()));
        r.setEstado(EstadoReserva.PENDIENTE_PAGO);

        try {
            r = reservaRepository.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // Segunda linea de defensa: la restriccion EXCLUDE de la base.
            throw ApiException.conflicto("Esas fechas acaban de ocuparse. Elige otro rango en el calendario.");
        }

        auditoria.registrar(usuario, "reserva_creada", "reserva", r.getId(), null,
                mapa("producto", p.getNombre(), "unidad", r.getUnidad().getCodigoInterno(),
                        "desde", r.getFechaInicio(), "hasta", r.getFechaFin(), "total", r.getTotal()));
        return mapper.uno(r, usuario);
    }

    /**
     * Pago simulado: sin pasarela real, pero con el flujo completo y rechazos reproducibles.
     * noRollbackFor: un rechazo responde 402 pero deja registrado el intento y su auditoria.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public ReservaResponse pagar(Usuario actor, UUID reservaId, PagoRequest req) {
        Reserva r = cargarPropia(actor, reservaId);
        if (r.getEstado() == EstadoReserva.CANCELADA) {
            throw ApiException.conflicto("La reserva expiró o fue cancelada. Vuelve a elegir las fechas.");
        }
        if (r.getEstado() != EstadoReserva.PENDIENTE_PAGO) {
            throw ApiException.conflicto("Esta reserva ya fue pagada");
        }

        PagoSimulado pago = pagoRepository.findByReservaId(r.getId()).orElseGet(() -> {
            PagoSimulado nuevo = new PagoSimulado();
            nuevo.setReserva(r);
            return nuevo;
        });
        pago.setMonto(r.getTotal());
        pago.setMetodo(req.metodo());

        String rechazo = procesar(pago, req);
        pago.setFechaPago(OffsetDateTime.now());
        pago.setReferencia(referencia());

        if (rechazo != null) {
            pago.setEstado(EstadoPago.RECHAZADO);
            pagoRepository.save(pago);
            auditoria.registrar(actor, "pago_rechazado", "reserva", r.getId(), null,
                    mapa("metodo", req.metodo().valor(), "motivo", rechazo));
            throw new ApiException(org.springframework.http.HttpStatus.PAYMENT_REQUIRED, "Pago rechazado", rechazo);
        }

        pago.setEstado(EstadoPago.APROBADO);
        pagoRepository.save(pago);
        r.setEstado(EstadoReserva.CONFIRMADA);

        Producto p = r.getProducto();
        notificaciones.notificar(r.getUsuario(), TipoNotificacion.RESERVA, "reserva", r.getId(),
                "Reserva " + r.getCodigo() + " confirmada: " + p.getNombre() + " del " + fecha(r.getFechaInicio())
                        + " al " + fecha(r.getFechaFin()) + ".");
        if (!p.getPropietario().getId().equals(r.getUsuario().getId())) {
            notificaciones.notificar(p.getPropietario(), TipoNotificacion.RESERVA, "reserva", r.getId(),
                    "Nueva reserva de \"" + p.getNombre() + "\" del " + fecha(r.getFechaInicio())
                            + " al " + fecha(r.getFechaFin()) + ". Prepara la unidad " + r.getUnidad().getCodigoInterno() + ".");
        }
        auditoria.registrar(actor, "reserva_confirmada", "reserva", r.getId(),
                mapa("estado", "pendiente_pago"),
                mapa("estado", "confirmada", "pago", pago.getReferencia(), "monto", pago.getMonto()));
        return mapper.uno(r, actor);
    }

    @Transactional(readOnly = true)
    public List<ReservaResponse> misReservas(Usuario usuario) {
        return mapper.lista(reservaRepository.findDeUsuario(usuario.getId()), usuario);
    }

    @Transactional(readOnly = true)
    public ReservaResponse detalle(Usuario solicitante, UUID id) {
        Reserva r = reservaRepository.findDetalle(id)
                .orElseThrow(() -> ApiException.noEncontrado("La reserva no existe"));
        boolean esArrendatario = r.getUsuario().getId().equals(solicitante.getId());
        boolean esPropietario = r.getProducto().getPropietario().getId().equals(solicitante.getId());
        boolean esRevisor = r.getProducto().getAdministradorRevisor() != null
                && r.getProducto().getAdministradorRevisor().getId().equals(solicitante.getId());
        if (!esArrendatario && !esPropietario && !esRevisor && !solicitante.esSuperadmin()) {
            throw ApiException.noEncontrado("La reserva no existe");
        }
        return mapper.uno(r, solicitante);
    }

    @Transactional
    public ReservaResponse cancelar(Usuario actor, UUID id, String motivo) {
        Reserva r = cargarPropia(actor, id);
        int horas = parametros.entero(ParametroService.CANCELACION_HORAS);
        if (!ReservaMapper.puedeCancelarUsuario(r, horas)) {
            if (r.getEstado() == EstadoReserva.CONFIRMADA) {
                throw ApiException.reglaNegocio("Ya no puedes cancelar en línea: la política exige hacerlo al menos "
                        + horas + " horas antes del inicio. Escríbenos a soporte para gestionarlo.");
            }
            throw ApiException.reglaNegocio("Esta reserva no se puede cancelar en su estado actual");
        }
        EstadoReserva anterior = r.getEstado();
        aplicarCancelacion(r, limpiar(motivo) == null ? "Cancelada por el usuario" : limpiar(motivo));

        Producto p = r.getProducto();
        if (anterior == EstadoReserva.CONFIRMADA && !p.getPropietario().getId().equals(actor.getId())) {
            notificaciones.notificar(p.getPropietario(), TipoNotificacion.RESERVA, "reserva", r.getId(),
                    "La reserva " + r.getCodigo() + " de \"" + p.getNombre() + "\" fue cancelada por el cliente. "
                            + "Las fechas quedaron libres.");
        }
        auditoria.registrar(actor, "reserva_cancelada", "reserva", r.getId(),
                mapa("estado", anterior.valor()), mapa("estado", "cancelada", "motivo", r.getMotivoCancelacion()));
        return mapper.uno(r, actor);
    }

    /** Libera las reservas que no se pagaron a tiempo para no bloquear el calendario. */
    @Scheduled(fixedDelayString = "${hawkify.reservas.expiracion-ms:60000}", initialDelay = 20000)
    @Transactional
    public void expirarPendientes() {
        int minutos = parametros.entero(ParametroService.RESERVA_MINUTOS_PAGO);
        List<Reserva> vencidas = reservaRepository.findByEstadoAndCreadoEnBefore(
                EstadoReserva.PENDIENTE_PAGO, OffsetDateTime.now().minusMinutes(minutos));
        for (Reserva r : vencidas) {
            aplicarCancelacion(r, "El pago no se completo en " + minutos + " minutos");
            notificaciones.notificar(r.getUsuario(), TipoNotificacion.RESERVA, "reserva", r.getId(),
                    "Liberamos la reserva " + r.getCodigo() + " porque el pago no se completo a tiempo.");
            auditoria.registrar(null, "reserva_expirada", "reserva", r.getId(),
                    mapa("estado", "pendiente_pago"), mapa("estado", "cancelada"));
        }
        if (!vencidas.isEmpty()) {
            log.info("Reservas pendientes expiradas: {}", vencidas.size());
        }
    }

    void aplicarCancelacion(Reserva r, String motivo) {
        r.setEstado(EstadoReserva.CANCELADA);
        r.setMotivoCancelacion(motivo);
        r.setCanceladoEn(OffsetDateTime.now());
        pagoRepository.findByReservaId(r.getId()).ifPresent(pago -> {
            if (pago.getEstado() == EstadoPago.APROBADO) {
                pago.setEstado(EstadoPago.REEMBOLSADO);
            }
        });
    }

    private Reserva cargarPropia(Usuario actor, UUID id) {
        Reserva r = reservaRepository.findDetalle(id)
                .orElseThrow(() -> ApiException.noEncontrado("La reserva no existe"));
        if (!r.getUsuario().getId().equals(actor.getId())) {
            throw ApiException.noEncontrado("La reserva no existe");
        }
        return r;
    }

    private String validarFechas(LocalDate inicio, LocalDate fin) {
        LocalDate hoy = LocalDate.now(ReservaMapper.ZONA);
        if (inicio.isBefore(hoy)) {
            return "La fecha de inicio no puede ser anterior a hoy";
        }
        if (fin.isBefore(inicio)) {
            return "La fecha de devolución debe ser igual o posterior a la de inicio";
        }
        int max = parametros.entero(ParametroService.RESERVA_DIAS_MAX);
        if (ChronoUnit.DAYS.between(inicio, fin) + 1 > max) {
            return "El alquiler máximo es de " + max + " días por reserva";
        }
        if (inicio.isAfter(hoy.plusMonths(6))) {
            return "Solo se puede reservar con hasta 6 meses de anticipación";
        }
        return null;
    }

    /** Documento y telefono son opcionales al registrarse pero obligatorios para alquilar. */
    private void completarDatosPersonales(Usuario u, CrearReservaRequest req) {
        String doc = limpiar(req.documentoIdentidad());
        String tel = limpiar(req.telefono());
        if (doc != null) {
            if (!doc.matches("[0-9A-Za-z.-]{5,20}")) {
                throw ApiException.invalido("El documento de identidad no tiene un formato válido");
            }
            u.setDocumentoIdentidad(doc);
        }
        if (tel != null) {
            if (!tel.matches("[0-9+ ()-]{7,20}")) {
                throw ApiException.invalido("El teléfono no tiene un formato válido");
            }
            u.setTelefono(tel);
        }
        if (u.getDocumentoIdentidad() == null) {
            throw ApiException.invalido("Necesitamos tu documento de identidad para respaldar el alquiler");
        }
        if (u.getTelefono() == null) {
            throw ApiException.invalido("Necesitamos un teléfono para coordinar la entrega");
        }
    }

    private String resolverDireccion(Usuario u, CrearReservaRequest req) {
        if (req.direccionId() != null) {
            Direccion d = direccionRepository.findByIdAndUsuarioId(req.direccionId(), u.getId())
                    .orElseThrow(() -> ApiException.invalido("La dirección seleccionada no existe"));
            return d.comoTexto();
        }
        String texto = limpiar(req.direccionTexto());
        if (texto == null || texto.length() < 8) {
            throw ApiException.invalido("Escribe la dirección de entrega completa (calle, número y ciudad)");
        }
        return texto;
    }

    /** @return motivo de rechazo, o null si el pago simulado se aprueba. */
    private static String procesar(PagoSimulado pago, PagoRequest req) {
        return switch (req.metodo()) {
            case TARJETA_SIMULADA -> {
                String numero = req.numeroTarjeta() == null ? "" : req.numeroTarjeta().replaceAll("[\\s-]", "");
                if (!numero.matches("\\d{13,19}")) {
                    throw ApiException.invalido("El número de tarjeta debe tener entre 13 y 19 dígitos");
                }
                if (limpiar(req.titular()) == null) {
                    throw ApiException.invalido("Escribe el nombre del titular como aparece en la tarjeta");
                }
                if (req.vencimiento() == null || !req.vencimiento().matches("(0[1-9]|1[0-2])/\\d{2}")) {
                    throw ApiException.invalido("El vencimiento debe tener el formato MM/AA");
                }
                if (req.cvv() == null || !req.cvv().matches("\\d{3,4}")) {
                    throw ApiException.invalido("El código de seguridad (CVV) debe tener 3 o 4 dígitos");
                }
                String ultimos = numero.substring(numero.length() - 4);
                String franquicia = switch (numero.charAt(0)) {
                    case '4' -> "Visa";
                    case '5', '2' -> "Mastercard";
                    case '3' -> "American Express";
                    default -> "Tarjeta";
                };
                pago.setDetalle(franquicia + " terminada en " + ultimos);
                if (numero.endsWith("0002")) {
                    yield "El banco emisor rechazó la transacción (fondos insuficientes). Prueba con otra tarjeta.";
                }
                if (numero.endsWith("0069")) {
                    yield "La tarjeta aparece vencida o bloqueada. Prueba con otra tarjeta.";
                }
                yield null;
            }
            case PSE_SIMULADO -> {
                String banco = limpiar(req.banco());
                if (banco == null) {
                    throw ApiException.invalido("Selecciona tu banco para pagar por PSE");
                }
                pago.setDetalle("PSE - " + banco);
                yield null;
            }
            case NEQUI_SIMULADO -> {
                String celular = req.celular() == null ? "" : req.celular().replaceAll("\\D", "");
                if (!celular.matches("3\\d{9}")) {
                    throw ApiException.invalido("El celular Nequi debe tener 10 dígitos y empezar por 3");
                }
                pago.setDetalle("Nequi terminado en " + celular.substring(6));
                yield null;
            }
        };
    }

    private static String referencia() {
        StringBuilder sb = new StringBuilder("HKP-")
                .append(LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)).append('-');
        for (int i = 0; i < 6; i++) {
            sb.append(ALFABETO.charAt(RANDOM.nextInt(ALFABETO.length())));
        }
        return sb.toString();
    }
}
