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

public interface ResenaRepository extends JpaRepository<Resena, UUID> {

    Optional<Resena> findByCalificacionId(UUID calificacionId);

    List<Resena> findByCalificacionIdIn(Collection<UUID> calificacionIds);

    @EntityGraph(attributePaths = {"calificacion", "calificacion.usuario", "calificacion.producto",
            "calificacion.producto.propietario", "calificacion.producto.administradorRevisor"})
    @Query("select r from Resena r where r.id = :id")
    Optional<Resena> findDetalle(@Param("id") UUID id);

    /** RF-17/RF-18: bandeja de moderacion con alcance por administrador. */
    @EntityGraph(attributePaths = {"calificacion", "calificacion.usuario", "calificacion.producto"})
    @Query(value = """
            select r from Resena r
            where (:todo = true
                   or r.calificacion.producto.administradorRevisor.id = :adminId
                   or r.calificacion.producto.propietario.id = :adminId)
              and (:estado is null or r.estadoModeracion = :estado)
            order by r.creadoEn desc
            """,
            countQuery = """
            select count(r) from Resena r
            where (:todo = true
                   or r.calificacion.producto.administradorRevisor.id = :adminId
                   or r.calificacion.producto.propietario.id = :adminId)
              and (:estado is null or r.estadoModeracion = :estado)
            """)
    Page<Resena> bandeja(@Param("todo") boolean todo,
                         @Param("adminId") UUID adminId,
                         @Param("estado") EstadoModeracion estado,
                         Pageable pageable);

    long countByEstadoModeracion(EstadoModeracion estado);
}
