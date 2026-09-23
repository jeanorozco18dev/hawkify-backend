package com.hawkify.auth;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RecuperacionService recuperacionService;

    public record RecuperarRequest(
            @NotBlank(message = "Escribe el correo de tu cuenta")
            @Email(message = "El correo no tiene un formato válido")
            String correo) {
    }

    public record RestablecerRequest(
            @NotBlank(message = "Falta el token del enlace") String token,
            @NotBlank(message = "Escribe la nueva contraseña")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "La contraseña debe combinar letras y números")
            String password) {
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse registrar(@Valid @RequestBody RegisterRequest request) {
        return authService.registrar(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Perfil del usuario autenticado (lo usa el frontend al cargar cada pagina). */
    @GetMapping("/me")
    @Transactional(readOnly = true)
    public UsuarioResponse yo(@AuthenticationPrincipal Usuario usuario) {
        return authService.aUsuarioResponse(usuario);
    }

    @PostMapping("/recuperar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> recuperar(@Valid @RequestBody RecuperarRequest req) {
        String enlace = recuperacionService.solicitar(req.correo());
        Map<String, String> body = new LinkedHashMap<>();
        body.put("mensaje", "Si el correo corresponde a una cuenta activa, te enviamos un enlace para "
                + "restablecer la contraseña. Revisa tu bandeja y la carpeta de spam.");
        if (enlace != null) {
            body.put("enlaceDemo", enlace);
        }
        return body;
    }

    @PostMapping("/restablecer")
    public Map<String, String> restablecer(@Valid @RequestBody RestablecerRequest req) {
        recuperacionService.restablecer(req.token(), req.password());
        return Map.of("mensaje", "Listo. Ya puedes iniciar sesión con tu nueva contraseña.");
    }
}
