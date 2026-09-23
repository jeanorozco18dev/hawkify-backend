package com.hawkify.catalogo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface ProductoRepository extends JpaRepository<Producto, UUID>, JpaSpecificationExecutor<Producto> {

    /**
     * Bloqueo de fila sobre el producto mientras se asigna una unidad a una
     * reserva: serializa reservas concurrentes del mismo producto (RNF-12).
     * La restriccion EXCLUDE de la tabla reserva es la segunda linea de defensa.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findByIdParaReservar(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"categoria", "marca", "propietario", "administradorRevisor"})
    @Query("select p from Producto p where p.id = :id")
    Optional<Producto> findDetalle(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"categoria", "marca", "imagenes"})
    @Query("select distinct p from Producto p where p.id in :ids")
    List<Producto> findConImagenes(@Param("ids") Collection<UUID> ids);

    @EntityGraph(attributePaths = {"categoria", "marca"})
    List<Producto> findByPropietarioIdOrderByCreadoEnDesc(UUID propietarioId);

    long countByEstadoPublicacion(EstadoPublicacion estado);

    @Query("""
            select count(p) from Producto p
            where p.estadoPublicacion = :estado
              and (p.administradorRevisor.id = :adminId or p.propietario.id = :adminId)
            """)
    long contarGestionadosPorEstado(@Param("adminId") UUID adminId, @Param("estado") EstadoPublicacion estado);

    @Query("select p.id from Producto p where p.administradorRevisor.id = :adminId or p.propietario.id = :adminId")
    List<UUID> idsGestionados(@Param("adminId") UUID adminId);
}
