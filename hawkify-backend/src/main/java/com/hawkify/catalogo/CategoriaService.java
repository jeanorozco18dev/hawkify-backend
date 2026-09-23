package com.hawkify.catalogo;

import static com.hawkify.common.Formatos.limpiar;
import static com.hawkify.common.Formatos.mapa;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.catalogo.CatalogoDtos.CategoriaResponse;
import com.hawkify.common.ApiException;
import com.hawkify.usuario.Usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** RF-30: el superadmin configura las categorias de herramientas. */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository repository;
    private final AuditoriaService auditoria;

    public record CategoriaRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
            String nombre,
            @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
            String descripcion,
            Boolean activa) {
    }

    @Transactional
    public CategoriaResponse crear(Usuario actor, CategoriaRequest req) {
        String nombre = req.nombre().trim();
        if (repository.existsByNombreIgnoreCase(nombre)) {
            throw ApiException.conflicto("Ya existe una categoría llamada \"" + nombre + "\"");
        }
        Categoria c = new Categoria();
        c.setNombre(nombre);
        c.setDescripcion(limpiar(req.descripcion()));
        c.setActiva(req.activa() == null || req.activa());
        c = repository.save(c);
        auditoria.registrar(actor, "categoria_creada", "categoria", c.getId(), null,
                mapa("nombre", c.getNombre(), "activa", c.isActiva()));
        return new CategoriaResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.isActiva(), 0);
    }

    @Transactional
    public CategoriaResponse actualizar(Usuario actor, UUID id, CategoriaRequest req) {
        Categoria c = repository.findById(id)
                .orElseThrow(() -> ApiException.noEncontrado("La categoría no existe"));
        String nombre = req.nombre().trim();
        if (repository.existsByNombreIgnoreCaseAndIdNot(nombre, id)) {
            throw ApiException.conflicto("Ya existe una categoría llamada \"" + nombre + "\"");
        }
        var previos = mapa("nombre", c.getNombre(), "activa", c.isActiva());
        c.setNombre(nombre);
        c.setDescripcion(limpiar(req.descripcion()));
        if (req.activa() != null) {
            c.setActiva(req.activa());
        }
        auditoria.registrar(actor, "categoria_actualizada", "categoria", c.getId(), previos,
                mapa("nombre", c.getNombre(), "activa", c.isActiva()));
        return new CategoriaResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.isActiva(), 0);
    }
}
