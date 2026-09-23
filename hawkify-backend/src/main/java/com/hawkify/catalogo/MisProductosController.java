package com.hawkify.catalogo;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.catalogo.CatalogoDtos.ProductoGestion;
import com.hawkify.catalogo.CatalogoDtos.ProductoRequest;
import com.hawkify.catalogo.CatalogoDtos.UnidadResponse;
import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** "Mis herramientas": cualquier usuario publica y administra sus propias fichas. */
@RestController
@RequestMapping("/api/mis-productos")
@RequiredArgsConstructor
public class MisProductosController {

    private final ProductoGestionService service;

    @GetMapping
    public List<ProductoGestion> listar(@AuthenticationPrincipal Usuario usuario) {
        return service.misProductos(usuario);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoGestion crear(@AuthenticationPrincipal Usuario usuario, @Valid @RequestBody ProductoRequest req) {
        return service.crear(usuario, req);
    }

    @PutMapping("/{id}")
    public ProductoGestion actualizar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id,
                                      @Valid @RequestBody ProductoRequest req) {
        return service.actualizar(usuario, id, req);
    }

    @PostMapping("/{id}/pausar")
    public ProductoGestion pausar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return service.pausarPropio(usuario, id, true);
    }

    @PostMapping("/{id}/reanudar")
    public ProductoGestion reanudar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return service.pausarPropio(usuario, id, false);
    }

    @GetMapping("/{id}/unidades")
    public List<UnidadResponse> unidades(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return service.unidades(usuario, id);
    }
}
