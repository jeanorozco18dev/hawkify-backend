package com.hawkify.resena;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeracionResenaRepository extends JpaRepository<ModeracionResena, UUID> {

    @EntityGraph(attributePaths = {"moderador"})
    List<ModeracionResena> findByResenaIdOrderByFechaDesc(UUID resenaId);
}
