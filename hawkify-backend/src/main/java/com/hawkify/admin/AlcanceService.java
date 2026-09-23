package com.hawkify.admin;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.catalogo.EstadoPublicacion;
import com.hawkify.catalogo.Producto;
import com.hawkify.common.ApiException;
import com.hawkify.usuario.CodigoPermiso;
import com.hawkify.usuario.PermisoRepository;
import com.hawkify.usuario.Usuario;

import lombok.RequiredArgsConstructor;

/**
 * Centraliza "quien puede tocar que" para los roles de staff:
 *  - Superadmin: todo el sistema.
 *  - Administrador: solo el inventario que gestiona (productos que reviso o
 *    publico) y solo con los permisos que el superadmin le asigno (RF-05).
 */
@Service
@RequiredArgsConstructor
public class AlcanceService {

    private final PermisoRepository permisoRepository;

    @Transactional(readOnly = true)
    public Set<CodigoPermiso> permisosDe(Usuario usuario) {
        if (usuario.esSuperadmin()) {
            return EnumSet.allOf(CodigoPermiso.class);
        }
        if (!usuario.esAdministrador()) {
            return EnumSet.noneOf(CodigoPermiso.class);
        }
        Set<CodigoPermiso> permisos = permisoRepository.codigosDeUsuario(usuario.getId()).stream()
                .map(CodigoPermiso::desde)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CodigoPermiso.class)));
        return permisos;
    }

    public void exigir(Usuario usuario, CodigoPermiso permiso) {
        if (!permisosDe(usuario).contains(permiso)) {
            throw ApiException.prohibido("Tu cuenta de administrador no tiene el permiso '"
                    + permiso.valor() + "'. Solicítalo al superadministrador.");
        }
    }

    public boolean gestiona(Usuario staff, Producto producto) {
        if (staff.esSuperadmin()) {
            return true;
        }
        UUID id = staff.getId();
        return (producto.getAdministradorRevisor() != null && id.equals(producto.getAdministradorRevisor().getId()))
                || id.equals(producto.getPropietario().getId());
    }

    /** Un producto pendiente lo puede revisar cualquier admin con permiso de aprobar. */
    public void exigirGestion(Usuario staff, Producto producto) {
        boolean pendiente = producto.getEstadoPublicacion() == EstadoPublicacion.PENDIENTE_APROBACION;
        if (!gestiona(staff, producto) && !pendiente) {
            throw ApiException.prohibido("Esta herramienta no está bajo tu gestión");
        }
    }
}
