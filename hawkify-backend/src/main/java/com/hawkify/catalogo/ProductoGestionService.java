package com.hawkify.catalogo;

import static com.hawkify.common.Formatos.cop;
import static com.hawkify.common.Formatos.limpiar;
import static com.hawkify.common.Formatos.mapa;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.admin.AlcanceService;
import com.hawkify.auditoria.AuditoriaService;
import com.hawkify.catalogo.CatalogoDtos.EspecificacionDto;
import com.hawkify.catalogo.CatalogoDtos.ProductoGestion;
import com.hawkify.catalogo.CatalogoDtos.ProductoRequest;
import com.hawkify.catalogo.CatalogoDtos.UnidadResponse;
import com.hawkify.common.ApiException;
import com.hawkify.common.PaginaResponse;
import com.hawkify.notificacion.NotificacionService;
import com.hawkify.notificacion.TipoNotificacion;
import com.hawkify.reserva.EstadoReserva;
import com.hawkify.reserva.ReservaRepository;
import com.hawkify.usuario.CodigoPermiso;
import com.hawkify.usuario.Usuario;
import com.hawkify.usuario.UsuarioRepository;
import com.hawkify.wishlist.ListaDeseosItemRepository;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * Ciclo de vida de una ficha: cualquier usuario publica (queda pendiente),
 * el staff aprueba/rechaza, pausa por mantenimiento o retira (RF-09..RF-11).
 */
@Service
@RequiredArgsConstructor
public class ProductoGestionService {

    private final ProductoRepository productoRepository;
    private final UnidadProductoRepository unidadRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final ReservaRepository reservaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ListaDeseosItemRepository listaDeseosRepository;
    private final AlcanceService alcance;
    private final AuditoriaService auditoria;
    private final NotificacionService notificaciones;

    // ------------------------------------------------------------------
    // Propietario (cualquier usuario) y staff
    // ------------------------------------------------------------------

    @Transactional
    public ProductoGestion crear(Usuario actor, ProductoRequest req) {
        Usuario propietario = usuarioRepository.getReferenceById(actor.getId());
        if (actor.esStaff()) {
            alcance.exigir(actor, CodigoPermiso.CATALOGO_GESTIONAR);
        }

        Producto p = new Producto();
        p.setPropietario(propietario);
        aplicar(p, req);

        // El staff publica directo: su propia revision es el filtro de calidad.
        if (actor.esStaff()) {
            p.setEstadoPublicacion(EstadoPublicacion.APROBADO);
            p.setAdministradorRevisor(propietario);
        } else {
            p.setEstadoPublicacion(EstadoPublicacion.PENDIENTE_APROBACION);
        }

        p = productoRepository.saveAndFlush(p);
        ajustarUnidades(p, req.unidades());

        auditoria.registrar(actor, "producto_creado", "producto", p.getId(), null,
                mapa("nombre", p.getNombre(), "tarifaDia", p.getTarifaDia(),
                        "estadoPublicacion", p.getEstadoPublicacion().valor(), "unidades", req.unidades()));
        return ProductoGestion.de(p);
    }

    @Transactional
    public ProductoGestion actualizar(Usuario actor, UUID id, ProductoRequest req) {
        Producto p = cargar(id);
        boolean esPropietario = actor.getId().equals(p.getPropietario().getId());

        if (actor.esStaff()) {
            alcance.exigir(actor, CodigoPermiso.CATALOGO_GESTIONAR);
            alcance.exigirGestion(actor, p);
        } else if (!esPropietario) {
            throw ApiException.prohibido("Solo puedes editar tus propias herramientas");
        }
        if (p.getEstadoPublicacion() == EstadoPublicacion.RETIRADO) {
            throw ApiException.reglaNegocio("La herramienta fue retirada del catalogo y ya no se puede editar");
        }

        var tarifaAnterior = p.getTarifaDia();
        var fisicoAnterior = p.getEstadoFisico();
        boolean reservableAntes = p.esReservable();

        aplicar(p, req);
        if (!actor.esStaff() && p.getEstadoPublicacion() == EstadoPublicacion.RECHAZADO) {
            // Corregida por el propietario: vuelve a la cola de revision.
            p.setEstadoPublicacion(EstadoPublicacion.PENDIENTE_APROBACION);
            p.setMotivoRechazo(null);
        }
        ajustarUnidades(p, req.unidades());
        productoRepository.flush();

        if (tarifaAnterior.compareTo(p.getTarifaDia()) != 0) {
            avisarCambioTarifa(p, tarifaAnterior);
        }
        avisarSiVuelveADisponible(p, reservableAntes);

        auditoria.registrar(actor, "producto_actualizado", "producto", p.getId(),
                mapa("tarifaDia", tarifaAnterior, "estadoFisico", fisicoAnterior.valor()),
                mapa("tarifaDia", p.getTarifaDia(), "estadoFisico", p.getEstadoFisico().valor(),
                        "unidades", req.unidades()));
        return ProductoGestion.de(p);
    }

    @Transactional(readOnly = true)
    public List<ProductoGestion> misProductos(Usuario usuario) {
        return productoRepository.findByPropietarioIdOrderByCreadoEnDesc(usuario.getId()).stream()
                .map(ProductoGestion::de)
                .toList();
    }

    /** El propietario puede pausar temporalmente su publicacion aprobada y reanudarla. */
    @Transactional
    public ProductoGestion pausarPropio(Usuario usuario, UUID id, boolean pausar) {
        Producto p = cargar(id);
        if (!usuario.getId().equals(p.getPropietario().getId())) {
            throw ApiException.prohibido("Solo puedes pausar tus propias herramientas");
        }
        boolean reservableAntes = p.esReservable();
        if (pausar) {
            exigirEstado(p, EstadoPublicacion.APROBADO, "Solo puedes pausar herramientas publicadas");
            p.setEstadoPublicacion(EstadoPublicacion.PAUSADO);
        } else {
            exigirEstado(p, EstadoPublicacion.PAUSADO, "La herramienta no está pausada");
            p.setEstadoPublicacion(EstadoPublicacion.APROBADO);
        }
        avisarSiVuelveADisponible(p, reservableAntes);
        auditoria.registrar(usuario, pausar ? "producto_pausado" : "producto_reactivado", "producto", p.getId(),
                null, mapa("estadoPublicacion", p.getEstadoPublicacion().valor()));
        return ProductoGestion.de(p);
    }

    // ------------------------------------------------------------------
    // Staff
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PaginaResponse<ProductoGestion> listarStaff(Usuario staff, EstadoPublicacion estado, String q,
                                                      String alcanceFiltro, int pagina, int tamano) {
        Specification<Producto> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            boolean soloPendientes = "pendientes".equals(alcanceFiltro);
            if (soloPendientes) {
                ps.add(cb.equal(root.get("estadoPublicacion"), EstadoPublicacion.PENDIENTE_APROBACION));
            } else if (!staff.esSuperadmin() || "gestionados".equals(alcanceFiltro)) {
                // Admin: su inventario + la cola de pendientes que puede revisar.
                ps.add(cb.or(
                        cb.equal(root.get("administradorRevisor").get("id"), staff.getId()),
                        cb.equal(root.get("propietario").get("id"), staff.getId()),
                        staff.esSuperadmin()
                                ? cb.disjunction()
                                : cb.equal(root.get("estadoPublicacion"), EstadoPublicacion.PENDIENTE_APROBACION)));
            }
            if (estado != null) {
                ps.add(cb.equal(root.get("estadoPublicacion"), estado));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), like),
                        cb.like(cb.lower(root.get("propietario").get("nombreCompleto")), like),
                        cb.like(cb.lower(root.get("propietario").get("correo")), like)));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var page = productoRepository.findAll(spec,
                PageRequest.of(pagina, tamano, Sort.by(Sort.Order.desc("actualizadoEn"))));
        return PaginaResponse.de(page, ProductoGestion::de);
    }

    @Transactional
    public ProductoGestion aprobar(Usuario staff, UUID id) {
        alcance.exigir(staff, CodigoPermiso.CATALOGO_APROBAR);
        Producto p = cargar(id);
        exigirEstado(p, EstadoPublicacion.PENDIENTE_APROBACION, "Solo se aprueban publicaciones pendientes");
        if (p.getUnidades().stream().noneMatch(u -> u.getEstado() == EstadoUnidad.DISPONIBLE)) {
            throw ApiException.reglaNegocio("La herramienta no tiene unidades operativas; no se puede publicar");
        }

        p.setEstadoPublicacion(EstadoPublicacion.APROBADO);
        p.setMotivoRechazo(null);
        p.setAdministradorRevisor(usuarioRepository.getReferenceById(staff.getId()));

        notificaciones.notificar(p.getPropietario(), TipoNotificacion.PUBLICACION, "producto", p.getId(),
                "Tu herramienta \"" + p.getNombre() + "\" fue aprobada y ya aparece en el catalogo.");
        auditoria.registrar(staff, "producto_aprobado", "producto", p.getId(),
                mapa("estadoPublicacion", "pendiente_aprobacion"), mapa("estadoPublicacion", "aprobado"));
        return ProductoGestion.de(p);
    }

    @Transactional
    public ProductoGestion rechazar(Usuario staff, UUID id, String motivo) {
        alcance.exigir(staff, CodigoPermiso.CATALOGO_APROBAR);
        Producto p = cargar(id);
        exigirEstado(p, EstadoPublicacion.PENDIENTE_APROBACION, "Solo se rechazan publicaciones pendientes");
        String m = exigirMotivo(motivo);

        p.setEstadoPublicacion(EstadoPublicacion.RECHAZADO);
        p.setMotivoRechazo(m);
        p.setAdministradorRevisor(usuarioRepository.getReferenceById(staff.getId()));

        notificaciones.notificar(p.getPropietario(), TipoNotificacion.PUBLICACION, "producto", p.getId(),
                "Tu herramienta \"" + p.getNombre() + "\" necesita ajustes antes de publicarse: " + m);
        auditoria.registrar(staff, "producto_rechazado", "producto", p.getId(),
                mapa("estadoPublicacion", "pendiente_aprobacion"), mapa("estadoPublicacion", "rechazado", "motivo", m));
        return ProductoGestion.de(p);
    }

    @Transactional
    public ProductoGestion pausar(Usuario staff, UUID id, String motivo) {
        Producto p = cargarGestionado(staff, id);
        exigirEstado(p, EstadoPublicacion.APROBADO, "Solo se pausan herramientas publicadas");
        p.setEstadoPublicacion(EstadoPublicacion.PAUSADO);
        auditoria.registrar(staff, "producto_pausado", "producto", p.getId(),
                mapa("estadoPublicacion", "aprobado"), mapa("estadoPublicacion", "pausado", "motivo", limpiar(motivo)));
        return ProductoGestion.de(p);
    }

    @Transactional
    public ProductoGestion reactivar(Usuario staff, UUID id) {
        Producto p = cargarGestionado(staff, id);
        exigirEstado(p, EstadoPublicacion.PAUSADO, "La herramienta no está pausada");
        boolean reservableAntes = p.esReservable();
        p.setEstadoPublicacion(EstadoPublicacion.APROBADO);
        avisarSiVuelveADisponible(p, reservableAntes);
        auditoria.registrar(staff, "producto_reactivado", "producto", p.getId(),
                mapa("estadoPublicacion", "pausado"), mapa("estadoPublicacion", "aprobado"));
        return ProductoGestion.de(p);
    }

    @Transactional
    public ProductoGestion retirar(Usuario staff, UUID id, String motivo) {
        Producto p = cargarGestionado(staff, id);
        if (p.getEstadoPublicacion() == EstadoPublicacion.RETIRADO) {
            throw ApiException.reglaNegocio("La herramienta ya está retirada");
        }
        if (reservaRepository.existenDeProductoEnEstados(p.getId(), EstadoReserva.ACTIVAS)) {
            throw ApiException.conflicto("La herramienta tiene reservas activas. Cancélalas o ciérralas antes de retirarla.");
        }
        String m = exigirMotivo(motivo);
        EstadoPublicacion anterior = p.getEstadoPublicacion();
        p.setEstadoPublicacion(EstadoPublicacion.RETIRADO);
        p.getUnidades().forEach(u -> u.setEstado(EstadoUnidad.RETIRADA));

        if (!p.getPropietario().getId().equals(staff.getId())) {
            notificaciones.notificar(p.getPropietario(), TipoNotificacion.PUBLICACION, "producto", p.getId(),
                    "Tu herramienta \"" + p.getNombre() + "\" fue retirada del catalogo: " + m);
        }
        auditoria.registrar(staff, "producto_retirado", "producto", p.getId(),
                mapa("estadoPublicacion", anterior.valor()), mapa("estadoPublicacion", "retirado", "motivo", m));
        return ProductoGestion.de(p);
    }

    @Transactional(readOnly = true)
    public List<UnidadResponse> unidades(Usuario actor, UUID productoId) {
        Producto p = cargar(productoId);
        if (!actor.esStaff() && !actor.getId().equals(p.getPropietario().getId())) {
            throw ApiException.prohibido("No puedes ver las unidades de esta herramienta");
        }
        return unidadRepository.findByProductoIdOrderByCodigoInternoAsc(productoId).stream()
                .map(UnidadResponse::de).toList();
    }

    @Transactional
    public UnidadResponse cambiarEstadoUnidad(Usuario staff, UUID unidadId, EstadoUnidad estado) {
        UnidadProducto u = unidadRepository.findById(unidadId)
                .orElseThrow(() -> ApiException.noEncontrado("La unidad no existe"));
        Producto p = cargarGestionado(staff, u.getProducto().getId());
        if (u.getEstado() == EstadoUnidad.RETIRADA) {
            throw ApiException.reglaNegocio("Una unidad retirada no se puede reactivar; agrega una unidad nueva");
        }
        if (estado == EstadoUnidad.RETIRADA && reservaRepository.exists((root, q, cb) -> cb.and(
                cb.equal(root.get("unidad").get("id"), u.getId()),
                root.get("estado").in(EstadoReserva.ACTIVAS)))) {
            throw ApiException.conflicto("La unidad tiene reservas activas; no se puede retirar todavía");
        }
        EstadoUnidad anterior = u.getEstado();
        boolean teniaOperativas = p.getUnidades().stream().anyMatch(x -> x.getEstado() == EstadoUnidad.DISPONIBLE);
        u.setEstado(estado);
        boolean tieneOperativas = p.getUnidades().stream().anyMatch(x -> x.getEstado() == EstadoUnidad.DISPONIBLE);

        if (p.esReservable() && !teniaOperativas && tieneOperativas) {
            avisarSiVuelveADisponible(p, false);
        }
        auditoria.registrar(staff, "unidad_estado", "producto", p.getId(),
                mapa("unidad", u.getCodigoInterno(), "estado", anterior.valor()),
                mapa("unidad", u.getCodigoInterno(), "estado", estado.valor()));
        return UnidadResponse.de(u);
    }

    // ------------------------------------------------------------------
    // Internos
    // ------------------------------------------------------------------

    private Producto cargar(UUID id) {
        return productoRepository.findDetalle(id)
                .orElseThrow(() -> ApiException.noEncontrado("La herramienta no existe"));
    }

    private Producto cargarGestionado(Usuario staff, UUID id) {
        alcance.exigir(staff, CodigoPermiso.CATALOGO_GESTIONAR);
        Producto p = cargar(id);
        if (!alcance.gestiona(staff, p)) {
            throw ApiException.prohibido("Esta herramienta no está bajo tu gestión");
        }
        return p;
    }

    private void aplicar(Producto p, ProductoRequest req) {
        Categoria categoria = categoriaRepository.findById(req.categoriaId())
                .filter(Categoria::isActiva)
                .orElseThrow(() -> ApiException.invalido("La categoría seleccionada no existe o está inactiva"));

        p.setNombre(req.nombre().trim());
        p.setCategoria(categoria);
        p.setMarca(resolverMarca(req.marca()));
        p.setDescripcion(limpiar(req.descripcion()));
        p.setCondicionesUso(limpiar(req.condicionesUso()));
        p.setPoliticaGarantia(limpiar(req.politicaGarantia()));
        p.setTarifaDia(req.tarifaDia().setScale(0, java.math.RoundingMode.HALF_UP));
        p.setEstadoFisico(req.estadoFisico());

        p.getImagenes().clear();
        List<String> imagenes = req.imagenes() == null ? List.of() : req.imagenes();
        for (int i = 0; i < imagenes.size(); i++) {
            p.getImagenes().add(new ProductoImagen(p, imagenes.get(i).trim(), (short) i, i == 0));
        }

        p.getEspecificaciones().clear();
        if (req.especificaciones() != null) {
            for (EspecificacionDto e : req.especificaciones()) {
                p.getEspecificaciones().add(new ProductoEspecificacion(p, e.clave().trim(), e.valor().trim()));
            }
        }
    }

    private Marca resolverMarca(String nombre) {
        String limpio = limpiar(nombre);
        if (limpio == null) {
            return null;
        }
        return marcaRepository.findByNombreIgnoreCase(limpio)
                .orElseGet(() -> marcaRepository.save(new Marca(limpio)));
    }

    /**
     * Lleva el numero de unidades no retiradas a 'objetivo': crea las que
     * faltan o retira las sobrantes que no tengan reservas activas.
     */
    private void ajustarUnidades(Producto p, int objetivo) {
        List<UnidadProducto> vigentes = p.getUnidades().stream()
                .filter(u -> u.getEstado() != EstadoUnidad.RETIRADA)
                .toList();
        int actuales = vigentes.size();

        if (objetivo > actuales) {
            int secuencia = p.getUnidades().size();
            for (int i = 0; i < objetivo - actuales; i++) {
                secuencia++;
                UnidadProducto u = new UnidadProducto(p, p.getCodigo() + "-" + String.format("%02d", secuencia));
                p.getUnidades().add(unidadRepository.save(u));
            }
        } else if (objetivo < actuales) {
            int porRetirar = actuales - objetivo;
            var activas = EnumSet.copyOf(EstadoReserva.ACTIVAS);
            List<UnidadProducto> candidatas = new ArrayList<>(vigentes);
            // Primero las que estan en mantenimiento, luego las disponibles.
            candidatas.sort((a, b) -> Boolean.compare(a.getEstado() == EstadoUnidad.DISPONIBLE,
                    b.getEstado() == EstadoUnidad.DISPONIBLE));
            for (UnidadProducto u : candidatas) {
                if (porRetirar == 0) {
                    break;
                }
                boolean ocupada = reservaRepository.exists((root, q, cb) -> cb.and(
                        cb.equal(root.get("unidad").get("id"), u.getId()),
                        root.get("estado").in(activas)));
                if (!ocupada) {
                    u.setEstado(EstadoUnidad.RETIRADA);
                    porRetirar--;
                }
            }
            if (porRetirar > 0) {
                throw ApiException.conflicto("No puedes bajar a " + objetivo
                        + " unidades: hay unidades con reservas activas. Intenta de nuevo cuando terminen.");
            }
        }
    }

    private void avisarCambioTarifa(Producto p, java.math.BigDecimal anterior) {
        if (p.getEstadoPublicacion() != EstadoPublicacion.APROBADO) {
            return;
        }
        String verbo = p.getTarifaDia().compareTo(anterior) < 0 ? "bajó" : "subió";
        notificaciones.notificarVarios(listaDeseosRepository.interesados(p.getId()), TipoNotificacion.CAMBIO_TARIFA,
                "producto", p.getId(), "La tarifa de \"" + p.getNombre() + "\" " + verbo + " de "
                        + cop(anterior) + " a " + cop(p.getTarifaDia()) + " por día.");
    }

    private void avisarSiVuelveADisponible(Producto p, boolean reservableAntes) {
        if (!reservableAntes && p.esReservable()) {
            notificaciones.notificarVarios(listaDeseosRepository.interesados(p.getId()),
                    TipoNotificacion.DISPONIBILIDAD, "producto", p.getId(),
                    "\"" + p.getNombre() + "\" de tu lista de deseos ya está disponible para alquilar.");
        }
    }

    private static void exigirEstado(Producto p, EstadoPublicacion esperado, String mensaje) {
        if (p.getEstadoPublicacion() != esperado) {
            throw ApiException.reglaNegocio(mensaje);
        }
    }

    private static String exigirMotivo(String motivo) {
        String m = limpiar(motivo);
        if (m == null || m.length() < 5) {
            throw ApiException.invalido("Escribe un motivo claro (mínimo 5 caracteres); el propietario lo verá");
        }
        return m.length() > 500 ? m.substring(0, 500) : m;
    }
}
