package com.hawkify.usuario;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    /**
     * Trae el rol junto con el usuario en la misma consulta (JOIN FETCH).
     * Necesario porque Rol es LAZY y open-in-view esta deshabilitado: sin esto,
     * acceder a usuario.getRol() fuera de una transaccion lanza LazyInitializationException
     * (ver CustomUserDetailsService y AuthService.login).
     */
    @Query("select u from Usuario u join fetch u.rol where u.correo = :correo")
    Optional<Usuario> findByCorreoConRol(@Param("correo") String correo);

    @Query("select u from Usuario u join fetch u.rol where u.id = :id")
    Optional<Usuario> findByIdConRol(@Param("id") UUID id);
}
