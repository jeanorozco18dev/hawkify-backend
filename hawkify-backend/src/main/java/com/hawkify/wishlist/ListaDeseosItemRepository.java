package com.hawkify.wishlist;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hawkify.usuario.Usuario;

public interface ListaDeseosItemRepository extends JpaRepository<ListaDeseosItem, UUID> {

    @EntityGraph(attributePaths = {"producto", "producto.categoria", "producto.marca", "producto.imagenes"})
    @Query("select distinct i from ListaDeseosItem i where i.usuario.id = :usuarioId order by i.agregadoEn desc")
    List<ListaDeseosItem> deUsuario(@Param("usuarioId") UUID usuarioId);

    Optional<ListaDeseosItem> findByUsuarioIdAndProductoId(UUID usuarioId, UUID productoId);

    boolean existsByUsuarioIdAndProductoId(UUID usuarioId, UUID productoId);

    @Query("select i.producto.id from ListaDeseosItem i where i.usuario.id = :usuarioId and i.producto.id in :ids")
    List<UUID> productosGuardados(@Param("usuarioId") UUID usuarioId, @Param("ids") Collection<UUID> ids);

    @Query("select i.usuario from ListaDeseosItem i where i.producto.id = :productoId")
    List<Usuario> interesados(@Param("productoId") UUID productoId);

    long countByProductoId(UUID productoId);

    @Modifying
    @Query("delete from ListaDeseosItem i where i.usuario.id = :usuarioId")
    void borrarDeUsuario(@Param("usuarioId") UUID usuarioId);
}
