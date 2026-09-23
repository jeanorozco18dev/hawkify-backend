package com.hawkify.common;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Error de negocio con estado HTTP propio. El GlobalExceptionHandler lo
 * traduce a un ApiError sin que cada servicio tenga que conocer la capa web.
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String titulo;

    public ApiException(HttpStatus status, String titulo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.titulo = titulo;
    }

    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, "No encontrado", mensaje);
    }

    public static ApiException conflicto(String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, "Conflicto", mensaje);
    }

    public static ApiException reglaNegocio(String mensaje) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "Operación no permitida", mensaje);
    }

    public static ApiException prohibido(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, "Acceso denegado", mensaje);
    }

    public static ApiException invalido(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "Solicitud inválida", mensaje);
    }
}
