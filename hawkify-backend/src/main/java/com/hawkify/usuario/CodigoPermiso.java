package com.hawkify.usuario;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Alcance asignable a cada administrador (RF-05). Espejo de los codigos
 * sembrados en la tabla permiso (schema.sql). El superadmin los tiene todos.
 */
public enum CodigoPermiso {
    CATALOGO_GESTIONAR,
    CATALOGO_APROBAR,
    USUARIOS_GESTIONAR,
    RESERVAS_GESTIONAR,
    RESENAS_MODERAR;

    @JsonValue
    public String valor() {
        return name().toLowerCase();
    }

    public static CodigoPermiso desde(String codigo) {
        return valueOf(codigo.toUpperCase());
    }
}
