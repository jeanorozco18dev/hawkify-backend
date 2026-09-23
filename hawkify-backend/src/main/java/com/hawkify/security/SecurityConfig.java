package com.hawkify.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.hawkify.common.ApiError;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.json.JsonMapper;

/**
 * RNF-05 (RBAC). Tres capas:
 *  1. Rutas publicas: catalogo, auth, parametros publicos, imagenes.
 *  2. /api/admin/** exige rol de staff; reportes, parametros y categorias
 *     (RF-29, RF-30) exigen superadmin.
 *  3. El alcance fino de cada administrador (sus permisos y su inventario)
 *     lo aplica AlcanceService dentro de cada servicio.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String SUPERADMIN = "SUPERADMIN";
    private static final String ADMINISTRADOR = "ADMINISTRADOR";

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final JsonMapper jsonMapper;

    @Value("${hawkify.cors.origenes}")
    private String origenesPermitidos;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/registro", "/api/auth/login",
                                "/api/auth/recuperar", "/api/auth/restablecer").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/productos", "/api/productos/**",
                                "/api/categorias", "/api/marcas", "/api/parametros/publicos",
                                "/uploads/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reservas/cotizar").permitAll()
                        .requestMatchers("/api/admin/reportes/**", "/api/admin/reportes",
                                "/api/admin/parametros/**", "/api/admin/parametros").hasRole(SUPERADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/admin/categorias").hasRole(SUPERADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/admin/categorias/**").hasRole(SUPERADMIN)
                        .requestMatchers("/api/admin/**").hasAnyRole(SUPERADMIN, ADMINISTRADOR)
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> escribirError(res, 401, "No autenticado",
                                "Inicia sesión para continuar. Si ya lo hiciste, tu sesión expiró."))
                        .accessDeniedHandler((req, res, e) -> escribirError(res, 403, "Acceso denegado",
                                "Tu rol no tiene permiso para esta seccion")))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origenes = Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        config.setAllowedOriginPatterns(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void escribirError(HttpServletResponse res, int status, String error, String mensaje)
            throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(jsonMapper.writeValueAsString(ApiError.of(status, error, mensaje)));
    }
}
