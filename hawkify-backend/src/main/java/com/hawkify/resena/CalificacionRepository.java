package com.hawkify.resena;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalificacionRepository extends JpaRepository<Calificacion, UUID> {

    Optional<Calificacion> findByReservaId(UUID reservaId);

    List<Calificacion> findByReservaIdIn(Collection<UUID> reservaIds);

    @EntityGraph(attributePaths = {"usuario", "producto", "reserva"})
    @Query("select c from Calificacion c where c.id = :id")
    Optional<Calificacion> findDetalle(@Param("id") UUID id);

    @Query("""
            select c.estrellas, count(c) from Calificacion c
            where c.producto.id = :productoId
            group by c.estrellas
            """)
    List<Object[]> distribucion(@Param("productoId") UUID productoId);

    /** Calificaciones de un producto (publico), mas recientes primero. */
    @EntityGraph(attributePaths = {"usuario"})
    @Query(value = "select c from Calificacion c where c.producto.id = :productoId order by c.creadoEn desc",
            countQuery = "select count(c) from Calificacion c where c.producto.id = :productoId")
    Page<Calificacion> deProducto(@Param("productoId") UUID productoId, Pageable pageable);

    /** RF-14: historial de calificaciones del inventario gestionado (null = todo). */
    @EntityGraph(attributePaths = {"usuario", "producto"})
    @Query(value = """
            select c from Calificacion c
            where (:todo = true
                   or c.producto.administradorRevisor.id = :adminId
                   or c.producto.propietario.id = :adminId)
              and (:maxEstrellas is null or c.estrellas <= :maxEstrellas)
            order by c.creadoEn desc
            """,
            countQuery = """
            select count(c) from Calificacion c
            where (:todo = true
                   or c.producto.administradorRevisor.id = :adminId
                   or c.producto.propietario.id = :adminId)
              and (:maxEstrellas is null or c.estrellas <= :maxEstrellas)
            """)
    Page<Calificacion> historial(@Param("todo") boolean todo,
                                 @Param("adminId") UUID adminId,
                                 @Param("maxEstrellas") Short maxEstrellas,
                                 Pageable pageable);

    @EntityGraph(attributePaths = {"producto", "producto.imagenes", "reserva"})
    @Query("select distinct c from Calificacion c where c.usuario.id = :usuarioId order by c.creadoEn desc")
    List<Calificacion> deUsuario(@Param("usuarioId") UUID usuarioId);
}
