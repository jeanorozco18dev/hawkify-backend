package com.hawkify.notificacion;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum TipoNotificacion {
    DISPONIBILIDAD,
    CAMBIO_TARIFA,
    RESERVA,
    MODERACION,
    PUBLICACION,
    SISTEMA;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<TipoNotificacion> {
        public Conversor() {
            super(TipoNotificacion.class);
        }
    }
}
