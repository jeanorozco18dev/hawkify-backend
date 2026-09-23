package com.hawkify.auth;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record UsuarioResponse(
        UUID id,
        String nombreCompleto,
        String correo,
        String rol,
        String estado,
        String telefono,
        String documentoIdentidad,
        String fotoPerfilUrl,
        OffsetDateTime creadoEn,
        OffsetDateTime suspendidoHasta,
        String motivoEstado,
        List<String> permisos
) {
}
