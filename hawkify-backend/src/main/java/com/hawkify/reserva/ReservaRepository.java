package com.hawkify.reserva;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<Reserva, UUID>, JpaSpecificationExecutor<Reserva> {

    @EntityGraph(attributePaths = {"usuario", "unidad", "unidad.producto", "unidad.producto.propietario",
            "unidad.producto.administradorRevisor"})
    @Query("select r from Reserva r where r.id = :id")
    Optional<Reserva> findDetalle(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"unidad", "unidad.producto", "unidad.producto.imagenes"})
    @Query("select distinct r from Reserva r where r.usuario.id = :usuarioId order by r.fechaInicio desc")
    List<Reserva> findDeUsuario(@Param("usuarioId") UUID usuarioId);

    /** Reservas activas de cualquier unidad del producto que tocan el rango (calendario). */
    @Query("""
            select r from Reserva r
            where r.unidad.producto.id = :productoId
              and r.estado <> com.hawkify.reserva.EstadoReserva.CANCELADA
              and r.fechaInicio <= :hasta
              and r.fechaFin >= :desde
            """)
    List<Reserva> activasDeProductoEnRango(@Param("productoId") UUID productoId,
                                           @Param("desde") LocalDate desde,
                                           @Param("hasta") LocalDate hasta);

    List<Reserva> findByEstadoAndCreadoEnBefore(EstadoReserva estado, OffsetDateTime limite);

    boolean existsByUsuarioIdAndEstadoIn(UUID usuarioId, Collection<EstadoReserva> estados);

    @Query("""
            select count(r) > 0 from Reserva r
            where r.unidad.producto.id = :productoId
              and r.estado in :estados
            """)
    boolean existenDeProductoEnEstados(@Param("productoId") UUID productoId,
                                       @Param("estados") Collection<EstadoReserva> estados);

    @Query("""
            select count(r) from Reserva r
            where r.estado in :estados
              and (r.unidad.producto.administradorRevisor.id = :adminId
                   or r.unidad.producto.propietario.id = :adminId)
            """)
    long contarGestionadasEnEstados(@Param("adminId") UUID adminId,
                                    @Param("estados") Collection<EstadoReserva> estados);

    long countByEstadoIn(Collection<EstadoReserva> estados);

    /** Reservas de productos del propietario (para "Mis herramientas"). */
    @EntityGraph(attributePaths = {"usuario", "unidad", "unidad.producto"})
    @Query("""
            select r from Reserva r
            where r.unidad.producto.propietario.id = :propietarioId
              and r.estado <> com.hawkify.reserva.EstadoReserva.CANCELADA
            order by r.fechaInicio desc
            """)
    List<Reserva> deProductosDePropietario(@Param("propietarioId") UUID propietarioId);
}
