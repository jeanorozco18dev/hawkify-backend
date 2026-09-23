package com.hawkify.catalogo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MarcaRepository extends JpaRepository<Marca, UUID> {

    List<Marca> findAllByOrderByNombreAsc();

    Optional<Marca> findByNombreIgnoreCase(String nombre);
}
