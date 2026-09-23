package com.hawkify.reserva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.hawkify.resena.EstadoModeracion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ReservaDtos {

    private ReservaDtos() {
    }

    public record CotizacionRequest(
            @NotNull(message = "Falta la herramienta") UUID productoId,
            @NotNull(message = "Selecciona la fecha de inicio") LocalDate fechaInicio,
            @NotNull(message = "Selecciona la fecha de devolución") LocalDate fechaFin,
            ModalidadEntrega modalidadEntrega) {
    }

    /** RF-23: desglose antes de confirmar. */
    public record Cotizacion(
            UUID productoId,
            String producto,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            int dias,
            BigDecimal tarifaDia,
            BigDecimal subtotal,
            BigDecimal costoEnvio,
            BigDecimal total,
            ModalidadEntrega modalidadEntrega,
            boolean disponible,
            int unidadesLibres,
            String mensaje,
            int horasCancelacion,
            String politicaCancelacion) {
    }

    public record CrearReservaRequest(
            @NotNull(message = "Falta la herramienta") UUID productoId,
            @NotNull(message = "Selecciona la fecha de inicio") LocalDate fechaInicio,
            @NotNull(message = "Selecciona la fecha de devolución") LocalDate fechaFin,
            @NotNull(message = "Elige como recibir la herramienta") ModalidadEntrega modalidadEntrega,
            UUID direccionId,
            @Size(max = 400, message = "La dirección no puede superar 400 caracteres") String direccionTexto,
            @Size(max = 30, message = "El teléfono no puede superar 30 caracteres") String telefono,
            @Size(max = 30, message = "El documento no puede superar 30 caracteres") String documentoIdentidad) {
    }

    public record PagoRequest(
            @NotNull(message = "Selecciona un método de pago") MetodoPago metodo,
            @Size(max = 120) String titular,
            @Size(max = 25) String numeroTarjeta,
            @Size(max = 7) String vencimiento,
            @Size(max = 4) String cvv,
            @Size(max = 60) String banco,
            @Size(max = 15) String celular) {
    }

    public record MotivoRequest(@Size(max = 500, message = "El motivo no puede superar 500 caracteres") String motivo) {
    }

    public record CambioEstadoRequest(
            @NotNull(message = "Indica el nuevo estado") EstadoReserva estado,
            @Size(max = 500, message = "El motivo no puede superar 500 caracteres") String motivo) {
    }

    public record DevolucionRequest(
            @NotNull(message = "Indica en que estado llegó el equipo") EstadoEquipo estadoEquipo,
            @Size(max = 2000, message = "Las observaciones no pueden superar 2000 caracteres") String observaciones) {
    }

    // ---------------- respuestas ----------------

    public record ProductoRef(UUID id, String codigo, String nombre, String imagen) {
    }

    public record PersonaRef(UUID id, String nombre, String correo, String telefono) {
    }

    public record PagoDto(MetodoPago metodo, EstadoPago estado, BigDecimal monto, String referencia,
                          String detalle, OffsetDateTime fechaPago) {
    }

    public record DevolucionDto(EstadoEquipo estadoEquipo, String observaciones, OffsetDateTime fecha,
                                String registradoPor) {
    }

    public record ResenaRef(UUID id, String texto, EstadoModeracion estado) {
    }

    public record CalificacionRef(UUID id, int estrellas, OffsetDateTime creadoEn, ResenaRef resena) {
    }

    public record ReservaResponse(
            UUID id,
            String codigo,
            ProductoRef producto,
            String unidad,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            int dias,
            BigDecimal tarifaDia,
            BigDecimal subtotal,
            BigDecimal costoEnvio,
            BigDecimal total,
            ModalidadEntrega modalidadEntrega,
            String direccionEntrega,
            EstadoReserva estado,
            String motivoCancelacion,
            OffsetDateTime creadoEn,
            OffsetDateTime canceladoEn,
            OffsetDateTime limitePagoEn,
            PagoDto pago,
            DevolucionDto devolucion,
            CalificacionRef calificacion,
            PersonaRef arrendatario,
            PersonaRef propietario,
            boolean puedeCancelar,
            boolean puedeCalificar) {
    }
}
