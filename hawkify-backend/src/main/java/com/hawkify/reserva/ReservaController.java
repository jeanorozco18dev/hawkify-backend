package com.hawkify.reserva;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.reserva.ReservaDtos.Cotizacion;
import com.hawkify.reserva.ReservaDtos.CotizacionRequest;
import com.hawkify.reserva.ReservaDtos.CrearReservaRequest;
import com.hawkify.reserva.ReservaDtos.MotivoRequest;
import com.hawkify.reserva.ReservaDtos.PagoRequest;
import com.hawkify.reserva.ReservaDtos.ReservaResponse;
import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService service;
    private final ReservaStaffService staffService;

    /** Publico: el visitante ve el desglose antes de iniciar sesion (RF-23). */
    @PostMapping("/api/reservas/cotizar")
    public Cotizacion cotizar(@Valid @RequestBody CotizacionRequest req) {
        return service.cotizar(req);
    }

    @PostMapping("/api/reservas")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaResponse crear(@AuthenticationPrincipal Usuario usuario, @Valid @RequestBody CrearReservaRequest req) {
        return service.crear(usuario, req);
    }

    @PostMapping("/api/reservas/{id}/pago")
    public ReservaResponse pagar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id,
                                 @Valid @RequestBody PagoRequest req) {
        return service.pagar(usuario, id, req);
    }

    @GetMapping("/api/reservas")
    public List<ReservaResponse> misReservas(@AuthenticationPrincipal Usuario usuario) {
        return service.misReservas(usuario);
    }

    @GetMapping("/api/reservas/{id}")
    public ReservaResponse detalle(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return service.detalle(usuario, id);
    }

    @PostMapping("/api/reservas/{id}/cancelar")
    public ReservaResponse cancelar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id,
                                    @Valid @RequestBody(required = false) MotivoRequest req) {
        return service.cancelar(usuario, id, req == null ? null : req.motivo());
    }

    /** Reservas recibidas sobre las herramientas que el usuario publico. */
    @GetMapping("/api/mis-productos/reservas")
    public List<ReservaResponse> deMisHerramientas(@AuthenticationPrincipal Usuario usuario) {
        return staffService.deMisHerramientas(usuario);
    }
}
