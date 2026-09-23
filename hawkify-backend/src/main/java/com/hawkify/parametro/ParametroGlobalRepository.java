package com.hawkify.parametro;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ParametroGlobalRepository extends JpaRepository<ParametroGlobal, UUID> {

    Optional<ParametroGlobal> findByClave(String clave);

    List<ParametroGlobal> findAllByOrderByClaveAsc();
}
