package com.hawkify.catalogo;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum EstadoFisico {
    NUEVO,
    USADO,
    EN_MANTENIMIENTO;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<EstadoFisico> {
        public Conversor() {
            super(EstadoFisico.class);
        }
    }
}
