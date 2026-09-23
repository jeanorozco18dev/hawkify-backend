package com.hawkify.catalogo;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoriaRepository extends JpaRepository<Categoria, UUID> {

    List<Categoria> findAllByOrderByNombreAsc();

    List<Categoria> findByActivaTrueOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, UUID id);

    /** [categoriaId, productos aprobados] para mostrar conteos en filtros. */
    @Query("""
            select p.categoria.id, count(p) from Producto p
            where p.estadoPublicacion = com.hawkify.catalogo.EstadoPublicacion.APROBADO
            group by p.categoria.id
            """)
    List<Object[]> contarProductosAprobados();
}
