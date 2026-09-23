package com.hawkify.reserva;

import com.fasterxml.jackson.annotation.JsonValue;
import com.hawkify.common.ConvertidorEnumMinusculas;

import jakarta.persistence.Converter;

public enum ModalidadEntrega {
    RECOGIDA,
    DOMICILIO;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    @Converter(autoApply = true)
    public static class Conversor extends ConvertidorEnumMinusculas<ModalidadEntrega> {
        public Conversor() {
            super(ModalidadEntrega.class);
        }
    }
}
