package com.hawkify.auth;

import java.util.UUID;

public record AuthResponse(
        String token,
        String tokenType,
        long expiraEnSegundos,
        UUID usuarioId,
        String nombreCompleto,
        String correo,
        String rol
) {
}
