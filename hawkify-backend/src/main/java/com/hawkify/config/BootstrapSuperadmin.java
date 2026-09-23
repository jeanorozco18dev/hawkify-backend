package com.hawkify.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.RolNombre;
import com.hawkify.usuario.RolRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * El superadmin no puede registrarse por la web. Si se definen
 * SUPERADMIN_CORREO y SUPERADMIN_PASSWORD y aun no existe ninguno, se crea al arrancar.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class BootstrapSuperadmin implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapSuperadmin.class);

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${hawkify.bootstrap.superadmin.correo:}")
    private String correo;

    @Value("${hawkify.bootstrap.superadmin.password:}")
    private String password;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (correo.isBlank() || password.isBlank() || usuarioRepository.existeSuperadmin()) {
            return;
        }
        Usuario u = Usuario.builder()
                .nombreCompleto("Superadministrador Hawkify")
                .correo(correo.trim().toLowerCase())
                .passwordHash(passwordEncoder.encode(password))
                .rol(rolRepository.findByNombre(RolNombre.SUPERADMIN).orElseThrow())
                .estado(EstadoUsuario.ACTIVO)
                .correoVerificado(true)
                .build();
        usuarioRepository.save(u);
        log.info("Superadmin inicial creado: {}", u.getCorreo());
    }
}
