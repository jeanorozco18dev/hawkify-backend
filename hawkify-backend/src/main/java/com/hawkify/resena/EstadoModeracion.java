package com.hawkify.resena;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum EstadoModeracion {
    VISIBLE,
    OCULTA,
    ELIMINADA;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<EstadoModeracion> {
        public Conversor() {
            super(EstadoModeracion.class);
        }
    }
}
