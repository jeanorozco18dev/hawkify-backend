package com.hawkify.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    /**
     * Trae el rol junto con el usuario en la misma consulta (JOIN FETCH).
     * Necesario porque Rol es LAZY y open-in-view esta deshabilitado.
     */
    @Query("select u from Usuario u join fetch u.rol where u.correo = :correo")
    Optional<Usuario> findByCorreoConRol(@Param("correo") String correo);

    @Query("select u from Usuario u join fetch u.rol where u.id = :id")
    Optional<Usuario> findByIdConRol(@Param("id") UUID id);

    @Query(value = """
            select u from Usuario u join fetch u.rol r
            where (:rol is null or r.nombre = :rol)
              and (:estado is null or u.estado = :estado)
              and u.eliminadoEn is null
              and (:q is null
                   or lower(u.nombreCompleto) like :q
                   or lower(u.correo) like :q
                   or u.documentoIdentidad like :q)
            """,
            countQuery = """
            select count(u) from Usuario u join u.rol r
            where (:rol is null or r.nombre = :rol)
              and (:estado is null or u.estado = :estado)
              and u.eliminadoEn is null
              and (:q is null
                   or lower(u.nombreCompleto) like :q
                   or lower(u.correo) like :q
                   or u.documentoIdentidad like :q)
            """)
    Page<Usuario> buscar(@Param("q") String q,
                         @Param("rol") String rol,
                         @Param("estado") EstadoUsuario estado,
                         Pageable pageable);

    @Query("select count(u) from Usuario u where u.rol.nombre = :rol and u.eliminadoEn is null")
    long contarPorRol(@Param("rol") String rol);

    @Query("select count(u) > 0 from Usuario u where u.rol.nombre = 'superadmin'")
    boolean existeSuperadmin();
}
