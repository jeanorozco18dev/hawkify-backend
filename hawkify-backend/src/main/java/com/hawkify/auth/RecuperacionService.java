package com.hawkify.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.common.ApiException;
import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.TokenRecuperacion;
import com.hawkify.usuario.TokenRecuperacionRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;

import lombok.RequiredArgsConstructor;

/**
 * RF-02: recuperacion de contrasena por correo. Mientras no haya proveedor de
 * correo configurado, el enlace se escribe en el log del servidor; con
 * hawkify.recuperacion.exponer-enlace=true tambien se devuelve en la
 * respuesta (solo para demos locales).
 */
@Service
@RequiredArgsConstructor
public class RecuperacionService {

    private static final Logger log = LoggerFactory.getLogger(RecuperacionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MINUTOS_VIGENCIA = 30;

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacionRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;

    @Value("${hawkify.frontend-url}")
    private String frontendUrl;

    @Value("${hawkify.recuperacion.exponer-enlace:false}")
    private boolean exponerEnlace;

    /** @return el enlace solo si exponer-enlace esta activo; si no, null. */
    @Transactional
    public String solicitar(String correo) {
        var usuario = usuarioRepository.findByCorreo(AuthService.normalizarCorreo(correo))
                .filter(u -> u.getEliminadoEn() == null && u.getEstado() != EstadoUsuario.DESACTIVADO);
        if (usuario.isEmpty()) {
            // Misma respuesta exista o no la cuenta: no se filtra que correos estan registrados.
            return null;
        }
        Usuario u = usuario.get();
        tokenRepository.invalidarVigentes(u.getId());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TokenRecuperacion t = new TokenRecuperacion();
        t.setUsuario(u);
        t.setTokenHash(sha256(token));
        t.setExpiraEn(OffsetDateTime.now().plusMinutes(MINUTOS_VIGENCIA));
        tokenRepository.save(t);

        String enlace = frontendUrl.replaceAll("/$", "") + "/restablecer.html?token=" + token;
        log.info("[correo simulado] Para: {} | Asunto: Restablece tu contraseña de Hawkify | Enlace: {}",
                u.getCorreo(), enlace);
        return exponerEnlace ? enlace : null;
    }

    @Transactional
    public void restablecer(String token, String nuevaPassword) {
        TokenRecuperacion t = tokenRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> ApiException.invalido("El enlace no es válido. Solicita uno nuevo."));
        if (t.isUsado()) {
            throw ApiException.invalido("Este enlace ya fue usado. Solicita uno nuevo si lo necesitas.");
        }
        if (t.getExpiraEn().isBefore(OffsetDateTime.now())) {
            throw ApiException.invalido("El enlace venció (dura " + MINUTOS_VIGENCIA + " minutos). Solicita uno nuevo.");
        }
        Usuario u = t.getUsuario();
        u.setPasswordHash(passwordEncoder.encode(nuevaPassword));
        u.setCorreoVerificado(true);
        t.setUsado(true);
        auditoria.registrar(u, "password_restablecida", "usuario", u.getId(), null, null);
    }

    private static String sha256(String valor) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
