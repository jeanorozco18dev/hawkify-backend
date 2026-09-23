package com.hawkify.notificacion;

import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.common.PaginaResponse;
import com.hawkify.notificacion.NotificacionService.NotificacionResponse;
import com.hawkify.usuario.Usuario;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notificaciones")
@RequiredArgsConstructor
public class NotificacionController {

    private final NotificacionService service;

    @GetMapping
    public PaginaResponse<NotificacionResponse> listar(@AuthenticationPrincipal Usuario usuario,
                                                       @RequestParam(defaultValue = "0") int pagina,
                                                       @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(usuario.getId(), Math.max(pagina, 0), Math.clamp(tamano, 1, 50));
    }

    @GetMapping("/no-leidas")
    public Map<String, Long> noLeidas(@AuthenticationPrincipal Usuario usuario) {
        return Map.of("total", service.noLeidas(usuario.getId()));
    }

    @PatchMapping("/{id}/leida")
    public NotificacionResponse marcarLeida(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return service.marcarLeida(usuario.getId(), id);
    }

    @PostMapping("/leer-todas")
    public Map<String, Integer> leerTodas(@AuthenticationPrincipal Usuario usuario) {
        return Map.of("actualizadas", service.marcarTodas(usuario.getId()));
    }
}
