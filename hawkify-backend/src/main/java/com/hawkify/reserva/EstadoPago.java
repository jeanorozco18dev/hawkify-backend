package com.hawkify.reserva;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum EstadoPago {
    PENDIENTE,
    APROBADO,
    RECHAZADO,
    REEMBOLSADO;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<EstadoPago> {
        public Conversor() {
            super(EstadoPago.class);
        }
    }
}
