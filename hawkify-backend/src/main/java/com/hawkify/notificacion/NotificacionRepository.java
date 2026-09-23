package com.hawkify.notificacion;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificacionRepository extends JpaRepository<Notificacion, UUID> {

    Page<Notificacion> findByUsuarioIdOrderByCreadoEnDesc(UUID usuarioId, Pageable pageable);

    long countByUsuarioIdAndLeidaFalse(UUID usuarioId);

    Optional<Notificacion> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.usuario.id = :usuarioId and n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("delete from Notificacion n where n.usuario.id = :usuarioId")
    void borrarDeUsuario(@Param("usuarioId") UUID usuarioId);
}
