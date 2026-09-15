package com.hawkify.usuario;

/**
 * Valores exactos de rol.nombre sembrados en schema.sql (tabla rol, ids 1-3).
 * Cualquier registro publico via /api/auth/registro asigna USUARIO_FINAL;
 * los roles ADMINISTRADOR y SUPERADMIN se asignan por fuera de ese flujo (RF-04, RF-05).
 */
public final class RolNombre {

    public static final String SUPERADMIN = "superadmin";
    public static final String ADMINISTRADOR = "administrador";
    public static final String USUARIO_FINAL = "usuario_final";

    private RolNombre() {
    }
}
