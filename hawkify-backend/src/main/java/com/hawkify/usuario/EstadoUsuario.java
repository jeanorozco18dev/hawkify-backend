package com.hawkify.usuario;

/**
 * Espejo del CHECK (estado IN ('activo','suspendido','desactivado')) de la tabla usuario (schema.sql).
 * Ver {@link EstadoUsuarioConverter} para el mapeo a minusculas contra la base de datos.
 */
public enum EstadoUsuario {
    ACTIVO,
    SUSPENDIDO,
    DESACTIVADO
}
