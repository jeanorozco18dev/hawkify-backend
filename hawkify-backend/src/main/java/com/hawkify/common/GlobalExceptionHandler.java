package com.hawkify.common;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hawkify.auth.CorreoYaRegistradoException;
import com.hawkify.auth.CredencialesInvalidasException;
import com.hawkify.auth.CuentaNoDisponibleException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> manejarValidacion(MethodArgumentNotValidException ex) {
        Map<String, String> errores = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                errores.put(fe.getField(), fe.getDefaultMessage()));

        ApiError body = ApiError.of(
                HttpStatus.BAD_REQUEST.value(),
                "Solicitud invalida",
                "Uno o mas campos no son validos",
                errores);

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(CorreoYaRegistradoException.class)
    public ResponseEntity<ApiError> manejarCorreoDuplicado(CorreoYaRegistradoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of(HttpStatus.CONFLICT.value(), "Correo duplicado", ex.getMessage()));
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ApiError> manejarCredencialesInvalidas(CredencialesInvalidasException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of(HttpStatus.UNAUTHORIZED.value(), "No autorizado", ex.getMessage()));
    }

    @ExceptionHandler(CuentaNoDisponibleException.class)
    public ResponseEntity<ApiError> manejarCuentaNoDisponible(CuentaNoDisponibleException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(HttpStatus.FORBIDDEN.value(), "Cuenta no disponible", ex.getMessage()));
    }
}
