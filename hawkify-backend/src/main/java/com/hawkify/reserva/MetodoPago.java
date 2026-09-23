package com.hawkify.reserva;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum MetodoPago {
    TARJETA_SIMULADA,
    PSE_SIMULADO,
    NEQUI_SIMULADO;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<MetodoPago> {
        public Conversor() {
            super(MetodoPago.class);
        }
    }
}
