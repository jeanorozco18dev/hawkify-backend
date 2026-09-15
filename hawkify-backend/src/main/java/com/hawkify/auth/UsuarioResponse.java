package com.hawkify.auth;

import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        String nombreCompleto,
        String correo,
        String rol,
        String estado
) {
}
