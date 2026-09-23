package com.hawkify.catalogo;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

/**
 * pendiente_aprobacion -> aprobado | rechazado
 * aprobado <-> pausado (baja temporal / mantenimiento, RF-10)
 * cualquiera -> retirado (baja definitiva)
 */
public enum EstadoPublicacion {
    PENDIENTE_APROBACION,
    APROBADO,
    RECHAZADO,
    PAUSADO,
    RETIRADO;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<EstadoPublicacion> {
        public Conversor() {
            super(EstadoPublicacion.class);
        }
    }
}
