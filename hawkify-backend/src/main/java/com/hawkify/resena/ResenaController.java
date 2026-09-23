package com.hawkify.resena;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.common.PaginaResponse;
import com.hawkify.resena.ResenaDtos.CalificacionHistorial;
import com.hawkify.resena.ResenaDtos.CalificarRequest;
import com.hawkify.resena.ResenaDtos.MiCalificacion;
import com.hawkify.resena.ResenaDtos.ModeracionHistorial;
import com.hawkify.resena.ResenaDtos.ModerarRequest;
import com.hawkify.resena.ResenaDtos.ReputacionProducto;
import com.hawkify.resena.ResenaDtos.ResenaModeracion;
import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ResenaController {

    private final ResenaService service;

    @GetMapping("/api/productos/{id}/resenas")
    public ReputacionProducto reputacion(@PathVariable UUID id,
                                         @RequestParam(defaultValue = "0") int pagina,
                                         @RequestParam(defaultValue = "6") int tamano) {
        return service.reputacion(id, Math.max(pagina, 0), Math.clamp(tamano, 1, 30));
    }

    @PostMapping("/api/reservas/{id}/calificacion")
    @ResponseStatus(HttpStatus.CREATED)
    public MiCalificacion calificar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id,
                                    @Valid @RequestBody CalificarRequest req) {
        return service.calificar(usuario, id, req);
    }

    @PutMapping("/api/calificaciones/{id}")
    public MiCalificacion editar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id,
                                 @Valid @RequestBody CalificarRequest req) {
        return service.editar(usuario, id, req);
    }

    @DeleteMapping("/api/resenas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        service.eliminarResena(usuario, id);
    }

    @GetMapping("/api/mis-calificaciones")
    public List<MiCalificacion> mias(@AuthenticationPrincipal Usuario usuario) {
        return service.misCalificaciones(usuario);
    }

    // ---------------- staff ----------------

    @GetMapping("/api/admin/resenas")
    public PaginaResponse<ResenaModeracion> bandeja(@AuthenticationPrincipal Usuario staff,
                                                    @RequestParam(required = false) EstadoModeracion estado,
                                                    @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "20") int tamano) {
        return service.bandeja(staff, estado, Math.max(pagina, 0), Math.clamp(tamano, 1, 100));
    }

    @PostMapping("/api/admin/resenas/{id}/moderar")
    public ResenaModeracion moderar(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                    @Valid @RequestBody ModerarRequest req) {
        return service.moderar(staff, id, req.accion(), req.motivo());
    }

    @GetMapping("/api/admin/resenas/{id}/historial")
    public List<ModeracionHistorial> historial(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id) {
        return service.historial(staff, id);
    }

    @GetMapping("/api/admin/calificaciones")
    public PaginaResponse<CalificacionHistorial> calificaciones(@AuthenticationPrincipal Usuario staff,
                                                               @RequestParam(required = false) Integer maxEstrellas,
                                                               @RequestParam(defaultValue = "0") int pagina,
                                                               @RequestParam(defaultValue = "20") int tamano) {
        return service.historialCalificaciones(staff, maxEstrellas, Math.max(pagina, 0), Math.clamp(tamano, 1, 100));
    }
}
