package com.hawkify.usuario;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PermisoRepository extends JpaRepository<Permiso, Short> {

    List<Permiso> findByCodigoIn(Collection<String> codigos);

    @Query(value = """
            select p.codigo from permiso p
            join usuario_permiso up on up.permiso_id = p.id
            where up.usuario_id = :usuarioId
            order by p.id
            """, nativeQuery = true)
    List<String> codigosDeUsuario(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query(value = "delete from usuario_permiso where usuario_id = :usuarioId", nativeQuery = true)
    void quitarTodos(@Param("usuarioId") UUID usuarioId);

    @Modifying
    @Query(value = "insert into usuario_permiso (usuario_id, permiso_id) values (:usuarioId, :permisoId)",
            nativeQuery = true)
    void asignar(@Param("usuarioId") UUID usuarioId, @Param("permisoId") Short permisoId);
}
