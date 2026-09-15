package com.hawkify.auth;

public class CorreoYaRegistradoException extends RuntimeException {

    public CorreoYaRegistradoException(String correo) {
        super("Ya existe una cuenta registrada con el correo " + correo);
    }
}
