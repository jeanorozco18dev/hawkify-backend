package com.hawkify.usuario;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class EstadoUsuarioConverter implements AttributeConverter<EstadoUsuario, String> {

    @Override
    public String convertToDatabaseColumn(EstadoUsuario attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public EstadoUsuario convertToEntityAttribute(String dbData) {
        return dbData == null ? null : EstadoUsuario.valueOf(dbData.toUpperCase());
    }
}
