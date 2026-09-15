package com.hawkify.auth;

/**
 * La cuenta existe y la contrasena es correcta, pero su estado (RF-04:
 * suspendida/desactivada) le impide iniciar sesion.
 */
public class CuentaNoDisponibleException extends RuntimeException {

    public CuentaNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
