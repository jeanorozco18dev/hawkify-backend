package com.hawkify.wishlist;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.catalogo.CatalogoDtos.ProductoResumen;
import com.hawkify.catalogo.CatalogoService;
import com.hawkify.catalogo.EstadoPublicacion;
import com.hawkify.catalogo.Producto;
import com.hawkify.catalogo.ProductoRepository;
import com.hawkify.common.ApiException;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/** RF-19..RF-21. Guardar no implica reserva ni pago; los avisos los emite ProductoGestionService. */
@RestController
@RequestMapping("/api/lista-deseos")
@RequiredArgsConstructor
public class ListaDeseosController {

    private final ListaDeseosItemRepository repository;
    private final ProductoRepository productoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CatalogoService catalogo;

    public record ItemListaDeseos(ProductoResumen producto, EstadoPublicacion estadoPublicacion,
                                  OffsetDateTime agregadoEn) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ItemListaDeseos> listar(@AuthenticationPrincipal Usuario usuario) {
        List<ListaDeseosItem> items = repository.deUsuario(usuario.getId());
        List<Producto> productos = items.stream().map(ListaDeseosItem::getProducto).toList();
        Map<UUID, ProductoResumen> resumenes = catalogo.aResumenes(productos, usuario).stream()
                .collect(Collectors.toMap(ProductoResumen::id, Function.identity()));
        return items.stream()
                .map(i -> new ItemListaDeseos(resumenes.get(i.getProducto().getId()),
                        i.getProducto().getEstadoPublicacion(), i.getAgregadoEn()))
                .toList();
    }

    @PutMapping("/{productoId}")
    @Transactional
    public Map<String, Object> agregar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID productoId) {
        if (usuario.esSuperadmin()) {
            throw ApiException.prohibido("La cuenta de superadministrador no usa lista de deseos");
        }
        Producto p = productoRepository.findById(productoId)
                .filter(x -> x.getEstadoPublicacion() == EstadoPublicacion.APROBADO
                        || x.getEstadoPublicacion() == EstadoPublicacion.PAUSADO)
                .orElseThrow(() -> ApiException.noEncontrado("La herramienta no está publicada"));
        if (!repository.existsByUsuarioIdAndProductoId(usuario.getId(), productoId)) {
            repository.save(new ListaDeseosItem(usuarioRepository.getReferenceById(usuario.getId()), p));
        }
        return Map.of("productoId", productoId, "enListaDeseos", true);
    }

    @DeleteMapping("/{productoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void quitar(@AuthenticationPrincipal Usuario usuario, @PathVariable UUID productoId) {
        repository.findByUsuarioIdAndProductoId(usuario.getId(), productoId).ifPresent(repository::delete);
    }
}
