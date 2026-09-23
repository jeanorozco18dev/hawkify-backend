package com.hawkify.usuario;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Espejo del CHECK (estado IN ('activo','suspendido','desactivado')) de la tabla usuario (schema.sql).
 * Ver {@link EstadoUsuarioConverter} para el mapeo a minusculas contra la base de datos.
 */
public enum EstadoUsuario {
    ACTIVO,
    SUSPENDIDO,
    DESACTIVADO;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }
}
