package com.hawkify.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.hawkify.admin.UsuarioAdminService.CrearUsuarioRequest;
import com.hawkify.admin.UsuarioAdminService.EditarUsuarioRequest;
import com.hawkify.admin.UsuarioAdminService.EstadoUsuarioRequest;
import com.hawkify.admin.UsuarioAdminService.PermisoResponse;
import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.auditoria.AuditoriaService.RegistroAuditoria;
import com.hawkify.auth.UsuarioResponse;
import com.hawkify.common.PaginaResponse;
import com.hawkify.parametro.ParametroService;
import com.hawkify.parametro.ParametroService.ParametroResponse;
import com.hawkify.usuario.EstadoUsuario;
import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Panel de staff: tablero, usuarios (RF-04/05), auditoria (RF-28),
 * reportes (RF-29) y parametros (RF-30). Las rutas de superadmin se
 * restringen por rol en SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UsuarioAdminService usuarios;
    private final ReporteService reportes;
    private final AuditoriaService auditoria;
    private final ParametroService parametros;

    public record ValorRequest(String valor) {
    }

    @GetMapping("/resumen")
    public ReporteService.ResumenStaff resumen(@AuthenticationPrincipal Usuario staff) {
        return reportes.resumen(staff);
    }

    // ---------------- usuarios ----------------

    @GetMapping("/usuarios")
    public PaginaResponse<UsuarioResponse> usuarios(@AuthenticationPrincipal Usuario staff,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(required = false) String rol,
                                                    @RequestParam(required = false) EstadoUsuario estado,
                                                    @RequestParam(defaultValue = "0") int pagina,
                                                    @RequestParam(defaultValue = "20") int tamano) {
        return usuarios.listar(staff, q, rol, estado, Math.max(pagina, 0), Math.clamp(tamano, 1, 100));
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    public UsuarioResponse crearUsuario(@AuthenticationPrincipal Usuario staff,
                                        @Valid @RequestBody CrearUsuarioRequest req) {
        return usuarios.crear(staff, req);
    }

    @PutMapping("/usuarios/{id}")
    public UsuarioResponse editarUsuario(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                         @Valid @RequestBody EditarUsuarioRequest req) {
        return usuarios.editar(staff, id, req);
    }

    @PatchMapping("/usuarios/{id}/estado")
    public UsuarioResponse estadoUsuario(@AuthenticationPrincipal Usuario staff, @PathVariable UUID id,
                                         @Valid @RequestBody EstadoUsuarioRequest req) {
        return usuarios.cambiarEstado(staff, id, req);
    }

    @GetMapping("/permisos")
    public List<PermisoResponse> permisos() {
        return usuarios.catalogoPermisos();
    }

    // ---------------- auditoria ----------------

    @GetMapping("/auditoria")
    public PaginaResponse<RegistroAuditoria> auditoria(
            @AuthenticationPrincipal Usuario staff,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "30") int tamano) {
        return auditoria.buscar(staff, entidad, accion, q, desde, hasta, Math.max(pagina, 0), Math.clamp(tamano, 1, 100));
    }

    @GetMapping("/auditoria/entidades")
    public List<String> entidadesAuditoria() {
        return auditoria.entidadesRegistradas();
    }

    // ---------------- superadmin ----------------

    @GetMapping("/reportes")
    public Map<String, Object> reporte(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return reportes.reporte(desde, hasta);
    }

    @GetMapping("/parametros")
    public List<ParametroResponse> parametros() {
        return parametros.listar();
    }

    @PutMapping("/parametros/{clave}")
    public ParametroResponse actualizarParametro(@AuthenticationPrincipal Usuario staff, @PathVariable String clave,
                                                 @RequestBody ValorRequest req) {
        return parametros.actualizar(staff, clave, req.valor());
    }
}
