package com.hawkify.usuario;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DireccionRepository extends JpaRepository<Direccion, UUID> {

    List<Direccion> findByUsuarioIdOrderByPredeterminadaDescEtiquetaAsc(UUID usuarioId);

    Optional<Direccion> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    long countByUsuarioId(UUID usuarioId);

    @Modifying
    @Query("update Direccion d set d.predeterminada = false where d.usuario.id = :usuarioId")
    void quitarPredeterminada(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("delete from Direccion d where d.usuario.id = :usuarioId")
    void borrarDeUsuario(@Param("usuarioId") UUID usuarioId);
}
