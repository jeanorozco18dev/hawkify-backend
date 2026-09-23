package com.hawkify.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRecuperacionRepository extends JpaRepository<TokenRecuperacion, UUID> {

    @Query("select t from TokenRecuperacion t join fetch t.usuario where t.tokenHash = :hash")
    Optional<TokenRecuperacion> findByTokenHash(@Param("hash") String hash);

    @Modifying
    @Query("update TokenRecuperacion t set t.usado = true where t.usuario.id = :usuarioId and t.usado = false")
    void invalidarVigentes(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query("delete from TokenRecuperacion t where t.usuario.id = :usuarioId")
    void borrarDeUsuario(@Param("usuarioId") UUID usuarioId);
}
