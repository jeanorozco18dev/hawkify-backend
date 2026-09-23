package com.hawkify.catalogo;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum EstadoUnidad {
    DISPONIBLE,
    EN_MANTENIMIENTO,
    RETIRADA;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<EstadoUnidad> {
        public Conversor() {
            super(EstadoUnidad.class);
        }
    }
}
