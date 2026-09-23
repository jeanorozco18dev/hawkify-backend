package com.hawkify.reserva;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PagoSimuladoRepository extends JpaRepository<PagoSimulado, UUID> {

    Optional<PagoSimulado> findByReservaId(UUID reservaId);

    List<PagoSimulado> findByReservaIdIn(Collection<UUID> reservaIds);
}
