package com.hawkify.perfil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.auth.UsuarioResponse;
import com.hawkify.perfil.PerfilService.DireccionRequest;
import com.hawkify.perfil.PerfilService.DireccionResponse;
import com.hawkify.perfil.PerfilService.EliminarCuentaRequest;
import com.hawkify.perfil.PerfilService.PasswordRequest;
import com.hawkify.perfil.PerfilService.PerfilRequest;
import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService service;

    @GetMapping
    public UsuarioResponse perfil(@AuthenticationPrincipal Usuario usuario) {
        return service.perfil(usuario);
    }

    @PutMapping
    public UsuarioResponse actualizar(@AuthenticationPrincipal Usuario usuario, @Valid @RequestBody PerfilRequest req) {
        return service.actualizar(usuario, req);
    }

    @PutMapping("/password")
    public Map<String, String> password(@AuthenticationPrincipal Usuario usuario,
                                        @Valid @RequestBody PasswordRequest req) {
        service.cambiarPassword(usuario, req);
        return Map.of("mensaje", "Contraseña actualizada");
    }

    @GetMapping("/direcciones")
    public List<DireccionResponse> direcciones(@AuthenticationPrincipal Usuario usuario) {
        return service.direcciones(usuario);
    }

    @PostMapping("/direcciones")
    @ResponseStatus(HttpStatus.CREATED)
    public DireccionResponse crearDireccion(@AuthenticationPrincipal Usuario usuario,
                                            @Valid @RequestBody DireccionRequest req) {
        return service.crearDireccion(usuario, req);
    }

    @PutMapping("/direcciones/{id}")
    public DireccionResponse actualizarDireccion(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id,
                                                 @Valid @RequestBody DireccionRequest req) {
        return service.actualizarDireccion(usuario, id, req);
    }

    @DeleteMapping("/direcciones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarDireccion(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        service.eliminarDireccion(usuario, id);
    }

    @GetMapping("/mis-datos")
    public Map<String, Object> exportar(@AuthenticationPrincipal Usuario usuario) {
        return service.exportar(usuario);
    }

    @PostMapping("/eliminar-cuenta")
    public Map<String, String> eliminarCuenta(@AuthenticationPrincipal Usuario usuario,
                                              @Valid @RequestBody EliminarCuentaRequest req) {
        service.eliminarCuenta(usuario, req);
        return Map.of("mensaje", "Tu cuenta fue eliminada y tus datos personales anonimizados.");
    }
}
