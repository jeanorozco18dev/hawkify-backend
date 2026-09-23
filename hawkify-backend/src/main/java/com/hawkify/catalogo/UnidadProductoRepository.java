package com.hawkify.catalogo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UnidadProductoRepository extends JpaRepository<UnidadProducto, UUID> {

    List<UnidadProducto> findByProductoIdOrderByCodigoInternoAsc(UUID productoId);

    List<UnidadProducto> findByProductoIdAndEstado(UUID productoId, EstadoUnidad estado);

    long countByProductoId(UUID productoId);

    /**
     * Unidades en estado 'disponible' sin ninguna reserva activa que se solape
     * con [desde, hasta]. Es la definicion operativa de disponibilidad (RF-22).
     */
    @Query("""
            select u from UnidadProducto u
            where u.producto.id = :productoId
              and u.estado = com.hawkify.catalogo.EstadoUnidad.DISPONIBLE
              and not exists (
                  select 1 from Reserva r
                  where r.unidad = u
                    and r.estado <> com.hawkify.reserva.EstadoReserva.CANCELADA
                    and r.fechaInicio <= :hasta
                    and r.fechaFin >= :desde)
            order by u.codigoInterno
            """)
    List<UnidadProducto> libres(@Param("productoId") UUID productoId,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta);

    /** [productoId, unidades libres en el rango] para las tarjetas del catalogo. */
    @Query("""
            select u.producto.id, count(u) from UnidadProducto u
            where u.producto.id in :ids
              and u.estado = com.hawkify.catalogo.EstadoUnidad.DISPONIBLE
              and not exists (
                  select 1 from Reserva r
                  where r.unidad = u
                    and r.estado <> com.hawkify.reserva.EstadoReserva.CANCELADA
                    and r.fechaInicio <= :hasta
                    and r.fechaFin >= :desde)
            group by u.producto.id
            """)
    List<Object[]> contarLibres(@Param("ids") Collection<UUID> ids,
                                @Param("desde") LocalDate desde,
                                @Param("hasta") LocalDate hasta);

    /** [productoId, unidades operativas (no retiradas)] */
    @Query("""
            select u.producto.id, count(u) from UnidadProducto u
            where u.producto.id in :ids
              and u.estado = com.hawkify.catalogo.EstadoUnidad.DISPONIBLE
            group by u.producto.id
            """)
    List<Object[]> contarOperativas(@Param("ids") Collection<UUID> ids);
}
