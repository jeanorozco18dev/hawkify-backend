package com.hawkify.reserva;

import java.util.EnumSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

/**
 * pendiente_pago -> confirmada (pago aprobado) | cancelada (expira o usuario)
 * confirmada     -> en_curso (entrega) | cancelada
 * en_curso       -> finalizada | con_incidencia (via devolucion)
 * con_incidencia -> finalizada (incidencia resuelta)
 */
public enum EstadoReserva {
    PENDIENTE_PAGO,
    CONFIRMADA,
    EN_CURSO,
    FINALIZADA,
    CANCELADA,
    CON_INCIDENCIA;

    /** Estados que ocupan la unidad en el calendario. */
    public static final Set<EstadoReserva> ACTIVAS =
            EnumSet.of(PENDIENTE_PAGO, CONFIRMADA, EN_CURSO, CON_INCIDENCIA);

    /** Estados que cuentan como ingreso en reportes. */
    public static final Set<EstadoReserva> FACTURABLES =
            EnumSet.of(CONFIRMADA, EN_CURSO, FINALIZADA, CON_INCIDENCIA);

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    public boolean puedeTransicionarA(EstadoReserva destino) {
        return switch (this) {
            case PENDIENTE_PAGO -> destino == CONFIRMADA || destino == CANCELADA;
            case CONFIRMADA -> destino == EN_CURSO || destino == CANCELADA;
            case EN_CURSO -> destino == FINALIZADA || destino == CON_INCIDENCIA;
            case CON_INCIDENCIA -> destino == FINALIZADA;
            case FINALIZADA, CANCELADA -> false;
        };
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<EstadoReserva> {
        public Conversor() {
            super(EstadoReserva.class);
        }
    }
}
