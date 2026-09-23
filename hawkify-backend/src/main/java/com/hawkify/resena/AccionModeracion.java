package com.hawkify.resena;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum AccionModeracion {
    OCULTAR,
    ELIMINAR,
    REVERTIR;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<AccionModeracion> {
        public Conversor() {
            super(AccionModeracion.class);
        }
    }
}
