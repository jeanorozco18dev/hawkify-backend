package com.hawkify.reserva;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DevolucionRepository extends JpaRepository<Devolucion, UUID> {

    @EntityGraph(attributePaths = {"registradoPor"})
    Optional<Devolucion> findByReservaId(UUID reservaId);

    boolean existsByReservaId(UUID reservaId);

    @EntityGraph(attributePaths = {"registradoPor"})
    List<Devolucion> findByReservaIdIn(Collection<UUID> reservaIds);
}
