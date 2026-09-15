package com.hawkify.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * Usado unicamente por el AuthenticationManager en el momento del login
 * (correo + password). Las peticiones autenticadas posteriores se resuelven
 * en JwtAuthenticationFilter, que carga el Usuario completo desde el token.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByCorreoConRol(correo)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + correo));

        return User.builder()
                .username(usuario.getCorreo())
                .password(usuario.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().getNombre().toUpperCase())))
                .disabled(usuario.getEstado() == EstadoUsuario.DESACTIVADO)
                .accountLocked(usuario.getEstado() == EstadoUsuario.SUSPENDIDO)
                .build();
    }
}
