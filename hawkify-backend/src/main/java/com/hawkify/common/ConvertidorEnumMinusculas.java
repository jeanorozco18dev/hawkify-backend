package com.hawkify.common;

import jakarta.persistence.AttributeConverter;

/**
 * Los dominios de estado del esquema (CHECK ... IN ('pendiente_pago', ...))
 * son el nombre del enum en minusculas. Cada enum declara un converter
 * anidado que extiende esta clase con autoApply = true.
 */
public abstract class ConvertidorEnumMinusculas<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> tipo;

    protected ConvertidorEnumMinusculas(Class<E> tipo) {
        this.tipo = tipo;
    }

    @Override
    public String convertToDatabaseColumn(E valor) {
        return valor == null ? null : valor.name().toLowerCase();
    }

    @Override
    public E convertToEntityAttribute(String valor) {
        return valor == null ? null : Enum.valueOf(tipo, valor.toUpperCase());
    }
}
