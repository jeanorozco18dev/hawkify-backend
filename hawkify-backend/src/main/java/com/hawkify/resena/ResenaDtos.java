package com.hawkify.resena;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ResenaDtos {

    private ResenaDtos() {
    }

    public record CalificarRequest(
            @NotNull(message = "Elige de 1 a 5 estrellas")
            @Min(value = 1, message = "La calificación mínima es 1 estrella")
            @Max(value = 5, message = "La calificación máxima es 5 estrellas")
            Integer estrellas,
            @Size(max = 2000, message = "La reseña no puede superar 2000 caracteres")
            String texto) {
    }

    public record ModerarRequest(
            @NotNull(message = "Indica la acción de moderación") AccionModeracion accion,
            @Size(max = 500, message = "El motivo no puede superar 500 caracteres") String motivo) {
    }

    public record ResenaPublica(UUID id, String autor, int estrellas, String texto, boolean editada,
                                OffsetDateTime fecha) {
    }

    /** Bloque de reputacion de la ficha: promedio, distribucion y comentarios visibles. */
    public record ReputacionProducto(
            BigDecimal promedio,
            int total,
            Map<Integer, Long> distribucion,
            List<ResenaPublica> items,
            int pagina,
            int totalPaginas) {
    }

    public record ProductoRef(UUID id, String nombre, String imagen) {
    }

    public record PersonaRef(UUID id, String nombre, String correo) {
    }

    public record MiCalificacion(UUID id, UUID reservaId, ProductoRef producto, int estrellas,
                                 UUID resenaId, String texto, EstadoModeracion estadoResena,
                                 OffsetDateTime creadoEn) {
    }

    public record ResenaModeracion(UUID id, UUID calificacionId, ProductoRef producto, PersonaRef autor,
                                   int estrellas, String texto, EstadoModeracion estado,
                                   OffsetDateTime creadoEn, OffsetDateTime eliminadoEn,
                                   boolean puedeModerar, boolean puedeRevertir) {
    }

    public record ModeracionHistorial(UUID id, AccionModeracion accion, String motivo, String moderador,
                                      OffsetDateTime fecha) {
    }

    public record CalificacionHistorial(UUID id, ProductoRef producto, PersonaRef autor, int estrellas,
                                        OffsetDateTime creadoEn) {
    }
}
