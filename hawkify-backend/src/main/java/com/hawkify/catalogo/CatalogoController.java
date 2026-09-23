package com.hawkify.catalogo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.catalogo.CatalogoDtos.CategoriaResponse;
import com.hawkify.catalogo.CatalogoDtos.Disponibilidad;
import com.hawkify.catalogo.CatalogoDtos.ProductoDetalle;
import com.hawkify.catalogo.CatalogoDtos.ProductoResumen;
import com.hawkify.catalogo.CatalogoDtos.Ref;
import com.hawkify.common.PaginaResponse;
import com.hawkify.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/** Endpoints publicos del catalogo (visitante incluido). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogoController {

    private final CatalogoService service;

    @GetMapping("/productos")
    public PaginaResponse<ProductoResumen> buscar(
            @AuthenticationPrincipal Usuario usuario,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID categoriaId,
            @RequestParam(required = false) List<UUID> marcaId,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax,
            @RequestParam(required = false) BigDecimal calificacionMin,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean soloDisponibles,
            @RequestParam(defaultValue = "relevancia") String orden,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "12") int tamano) {

        var filtros = new CatalogoService.Filtros(q, categoriaId, marcaId, precioMin, precioMax, calificacionMin,
                desde, hasta, soloDisponibles, orden, Math.max(pagina, 0), Math.clamp(tamano, 1, 48));
        return service.buscar(filtros, usuario);
    }

    @GetMapping("/productos/{id}")
    public ProductoDetalle detalle(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID id) {
        return service.detalle(id, usuario);
    }

    @GetMapping("/productos/{id}/disponibilidad")
    public Disponibilidad disponibilidad(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return service.disponibilidad(id, desde, hasta);
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return service.categorias(false);
    }

    @GetMapping("/marcas")
    public List<Ref> marcas() {
        return service.marcas();
    }
}
