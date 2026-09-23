package com.hawkify.reserva;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum EstadoEquipo {
    SIN_NOVEDAD,
    CON_DANO,
    PERDIDA;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<EstadoEquipo> {
        public Conversor() {
            super(EstadoEquipo.class);
        }
    }
}
