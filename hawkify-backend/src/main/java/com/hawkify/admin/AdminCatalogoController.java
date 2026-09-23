package com.hawkify.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.catalogo.CatalogoDtos.CategoriaResponse;
import com.hawkify.catalogo.CatalogoDtos.ProductoGestion;
import com.hawkify.catalogo.CatalogoDtos.ProductoRequest;
import com.hawkify.catalogo.CatalogoDtos.UnidadResponse;
import com.hawkify.catalogo.CatalogoService;
import com.hawkify.catalogo.CategoriaService;
import com.hawkify.catalogo.CategoriaService.CategoriaRequest;
import com.hawkify.catalogo.EstadoPublicacion;
import com.hawkify.catalogo.EstadoUnidad;
import com.hawkify.catalogo.ProductoGestionService;
import com.hawkify.common.PaginaResponse;
import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/** RF-09, RF-10, RF-11, RF-30 (categorias). Protegido por rol en SecurityConfig. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCatalogoController {

    private final ProductoGestionService productos;
    private final CategoriaService categorias;
    private final CatalogoService catalogo;

    public record MotivoRequest(String motivo) {
    }

    public record EstadoUnidadRequest(@NotNull(message = "Indica el nuevo estado") EstadoUnidad estado) {
    }

    @GetMapping("/productos")
    public PaginaResponse<ProductoGestion> listar(@AuthenticationPrincipal Usuario staff,
                                                  @RequestParam(required = false) EstadoPublicacion estado,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "todos") String alcance,
                                                  @RequestParam(defaultValue = "0") int pagina,
                                                  @RequestParam(defaultValue = "20") int tamano) {
        return productos.listarStaff(staff, estado, q, alcance, Math.max(pagina, 0), Math.clamp(tamano, 1, 100));
    }

    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductoGestion crear(@AuthenticationPrincipal Usuario staff, @Valid @RequestBody ProductoRequest req) {
        return productos.crear(staff, req);
    }

    @PutMapping("/productos/{id}")
    public ProductoGestion actualizar(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                      @Valid @RequestBody ProductoRequest req) {
        return productos.actualizar(staff, id, req);
    }

    @PostMapping("/productos/{id}/aprobar")
    public ProductoGestion aprobar(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id) {
        return productos.aprobar(staff, id);
    }

    @PostMapping("/productos/{id}/rechazar")
    public ProductoGestion rechazar(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                    @RequestBody MotivoRequest req) {
        return productos.rechazar(staff, id, req.motivo());
    }

    @PostMapping("/productos/{id}/pausar")
    public ProductoGestion pausar(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                  @RequestBody(required = false) MotivoRequest req) {
        return productos.pausar(staff, id, req == null ? null : req.motivo());
    }

    @PostMapping("/productos/{id}/reactivar")
    public ProductoGestion reactivar(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id) {
        return productos.reactivar(staff, id);
    }

    @PostMapping("/productos/{id}/retirar")
    public ProductoGestion retirar(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                   @RequestBody MotivoRequest req) {
        return productos.retirar(staff, id, req.motivo());
    }

    @GetMapping("/productos/{id}/unidades")
    public List<UnidadResponse> unidades(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id) {
        return productos.unidades(staff, id);
    }

    @PatchMapping("/unidades/{id}")
    public UnidadResponse estadoUnidad(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                       @Valid @RequestBody EstadoUnidadRequest req) {
        return productos.cambiarEstadoUnidad(staff, id, req.estado());
    }

    @GetMapping("/categorias")
    public List<CategoriaResponse> categorias() {
        return catalogo.categorias(true);
    }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crearCategoria(@AuthenticationPrincipal Usuario staff,
                                            @Valid @RequestBody CategoriaRequest req) {
        return categorias.crear(staff, req);
    }

    @PutMapping("/categorias/{id}")
    public CategoriaResponse actualizarCategoria(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                                 @Valid @RequestBody CategoriaRequest req) {
        return categorias.actualizar(staff, id, req);
    }
}
