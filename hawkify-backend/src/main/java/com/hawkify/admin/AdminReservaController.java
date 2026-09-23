package com.hawkify.admin;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.common.PaginaResponse;
import com.hawkify.reserva.EstadoReserva;
import com.hawkify.reserva.ReservaDtos.CambioEstadoRequest;
import com.hawkify.reserva.ReservaDtos.DevolucionRequest;
import com.hawkify.reserva.ReservaDtos.ReservaResponse;
import com.hawkify.reserva.ReservaStaffService;
import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** RF-25, RF-26, RF-27. */
@RestController
@RequestMapping("/api/admin/reservas")
@RequiredArgsConstructor
public class AdminReservaController {

    private final ReservaStaffService service;

    @GetMapping
    public PaginaResponse<ReservaResponse> listar(
            @AuthenticationPrincipal Usuario staff,
            @RequestParam(required = false) EstadoReserva estado,
            @RequestParam(required = false) UUID productoId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return service.listar(staff, new ReservaStaffService.Filtros(estado, productoId, q, desde, hasta,
                Math.max(pagina, 0), Math.clamp(tamano, 1, 300)));
    }

    @GetMapping("/{id}")
    public ReservaResponse detalle(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id) {
        return service.detalle(staff, id);
    }

    @PatchMapping("/{id}/estado")
    public ReservaResponse cambiarEstado(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                         @Valid @RequestBody CambioEstadoRequest req) {
        return service.cambiarEstado(staff, id, req.estado(), req.motivo());
    }

    @PostMapping("/{id}/devolucion")
    public ReservaResponse devolucion(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                      @Valid @RequestBody DevolucionRequest req) {
        return service.registrarDevolucion(staff, id, req);
    }
}
