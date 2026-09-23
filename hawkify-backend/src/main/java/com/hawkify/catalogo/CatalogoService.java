package com.hawkify.catalogo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hawkify.catalogo.CatalogoDtos.CategoriaResponse;
import com.hawkify.catalogo.CatalogoDtos.DiaDisponibilidad;
import com.hawkify.catalogo.CatalogoDtos.Disponibilidad;
import com.hawkify.catalogo.CatalogoDtos.EspecificacionDto;
import com.hawkify.catalogo.CatalogoDtos.ImagenDto;
import com.hawkify.catalogo.CatalogoDtos.PersonaRef;
import com.hawkify.catalogo.CatalogoDtos.ProductoDetalle;
import com.hawkify.catalogo.CatalogoDtos.ProductoResumen;
import com.hawkify.catalogo.CatalogoDtos.Ref;
import com.hawkify.common.ApiException;
import com.hawkify.common.PaginaResponse;
import com.hawkify.parametro.ParametroService;
import com.hawkify.reserva.EstadoReserva;
import com.hawkify.reserva.Reserva;
import com.hawkify.reserva.ReservaRepository;
import com.hawkify.usuario.Usuario;
import com.hawkify.wishlist.ListaDeseosItemRepository;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoService {

    private static final int MAX_DIAS_CALENDARIO = 186;

    private final ProductoRepository productoRepository;
    private final UnidadProductoRepository unidadRepository;
    private final ReservaRepository reservaRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final ListaDeseosItemRepository listaDeseosRepository;
    private final ParametroService parametros;

    public record Filtros(
            String q,
            UUID categoriaId,
            List<UUID> marcaIds,
            BigDecimal precioMin,
            BigDecimal precioMax,
            BigDecimal calificacionMin,
            LocalDate desde,
            LocalDate hasta,
            boolean soloDisponibles,
            String orden,
            int pagina,
            int tamano
    ) {
    }

    /** RF-06 / RF-07: catalogo con filtros avanzados. Solo fichas aprobadas. */
    public PaginaResponse<ProductoResumen> buscar(Filtros f, Usuario usuario) {
        LocalDate desde = f.desde();
        LocalDate hasta = f.hasta();
        if (desde != null && hasta == null) {
            hasta = desde;
        }
        if (desde == null && hasta != null) {
            desde = hasta;
        }
        if (desde == null && f.soloDisponibles()) {
            desde = LocalDate.now();
            hasta = desde;
        }
        if (desde != null && hasta.isBefore(desde)) {
            throw ApiException.invalido("La fecha final no puede ser anterior a la inicial");
        }

        Specification<Producto> spec = especificacion(f, desde, hasta);
        Page<Producto> page = productoRepository.findAll(spec, PageRequest.of(f.pagina(), f.tamano(), orden(f.orden())));
        return PaginaResponse.de(aResumenes(page.getContent(), usuario), f.pagina(), f.tamano(), page.getTotalElements());
    }

    public List<ProductoResumen> aResumenes(List<Producto> productos, Usuario usuario) {
        if (productos.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = productos.stream().map(Producto::getId).toList();
        LocalDate hoy = LocalDate.now();
        Map<UUID, Long> libres = aMapa(unidadRepository.contarLibres(ids, hoy, hoy));
        Map<UUID, Long> operativas = aMapa(unidadRepository.contarOperativas(ids));
        Set<UUID> guardados = usuario == null
                ? Set.of()
                : new HashSet<>(listaDeseosRepository.productosGuardados(usuario.getId(), ids));

        return productos.stream().map(p -> {
            long libresHoy = p.esReservable() ? libres.getOrDefault(p.getId(), 0L) : 0L;
            return new ProductoResumen(
                    p.getId(), p.getCodigo(), p.getNombre(), Ref.de(p.getCategoria()), Ref.de(p.getMarca()),
                    p.getTarifaDia(), p.getCalificacionPromedio(), p.getTotalCalificaciones(),
                    p.getImagenPrincipal(), p.getEstadoFisico(), libresHoy > 0, libresHoy,
                    operativas.getOrDefault(p.getId(), 0L), guardados.contains(p.getId()),
                    p.getPropietario().getNombrePublico());
        }).toList();
    }

    /** RF-08: ficha detallada. Fichas no aprobadas solo las ve su propietario o el staff. */
    public ProductoDetalle detalle(UUID id, Usuario usuario) {
        Producto p = productoRepository.findDetalle(id)
                .orElseThrow(() -> ApiException.noEncontrado("La herramienta no existe o fue retirada"));

        boolean esPropio = usuario != null && usuario.getId().equals(p.getPropietario().getId());
        boolean esStaff = usuario != null && usuario.esStaff();
        if (p.getEstadoPublicacion() != EstadoPublicacion.APROBADO && !esPropio && !esStaff) {
            throw ApiException.noEncontrado("La herramienta no está publicada en este momento");
        }

        LocalDate hoy = LocalDate.now();
        long libresHoy = p.esReservable() ? unidadRepository.libres(p.getId(), hoy, hoy).size() : 0;
        long operativas = p.getUnidades().stream().filter(u -> u.getEstado() == EstadoUnidad.DISPONIBLE).count();
        boolean enLista = usuario != null
                && listaDeseosRepository.existsByUsuarioIdAndProductoId(usuario.getId(), p.getId());

        boolean garantiaPorDefecto = p.getPoliticaGarantia() == null || p.getPoliticaGarantia().isBlank();
        String garantia = garantiaPorDefecto ? parametros.texto(ParametroService.GARANTIA_BASE) : p.getPoliticaGarantia();

        return new ProductoDetalle(
                p.getId(), p.getCodigo(), p.getNombre(), Ref.de(p.getCategoria()), Ref.de(p.getMarca()),
                p.getDescripcion(), p.getCondicionesUso(), garantia, garantiaPorDefecto, p.getTarifaDia(),
                p.getCalificacionPromedio(), p.getTotalCalificaciones(), p.getEstadoPublicacion(),
                p.getEstadoFisico(), esPropio || esStaff ? p.getMotivoRechazo() : null, p.esReservable(),
                operativas, libresHoy,
                p.getImagenes().stream().map(i -> new ImagenDto(i.getUrl(), i.isEsPrincipal())).toList(),
                p.getEspecificaciones().stream().map(e -> new EspecificacionDto(e.getClave(), e.getValor())).toList(),
                PersonaRef.publica(p.getPropietario()), p.getPropietario().getCreadoEn(),
                esPropio, enLista, p.getCreadoEn());
    }

    /**
     * RF-22: unidades libres por dia. Un dia con 0 disponibles se bloquea en el
     * calendario. Solo cuenta unidades en estado 'disponible'.
     */
    public Disponibilidad disponibilidad(UUID productoId, LocalDate desde, LocalDate hasta) {
        Producto p = productoRepository.findById(productoId)
                .orElseThrow(() -> ApiException.noEncontrado("La herramienta no existe"));

        LocalDate inicio = desde == null ? LocalDate.now() : desde;
        LocalDate fin = hasta == null ? inicio.plusDays(89) : hasta;
        if (fin.isBefore(inicio)) {
            throw ApiException.invalido("La fecha final no puede ser anterior a la inicial");
        }
        if (ChronoUnit.DAYS.between(inicio, fin) > MAX_DIAS_CALENDARIO) {
            fin = inicio.plusDays(MAX_DIAS_CALENDARIO);
        }

        List<UnidadProducto> unidades = unidadRepository.findByProductoIdAndEstado(productoId, EstadoUnidad.DISPONIBLE);
        Set<UUID> idsOperativas = new HashSet<>();
        unidades.forEach(u -> idsOperativas.add(u.getId()));

        List<Reserva> reservas = reservaRepository.activasDeProductoEnRango(productoId, inicio, fin);
        boolean reservable = p.esReservable();

        List<DiaDisponibilidad> dias = new ArrayList<>();
        for (LocalDate d = inicio; !d.isAfter(fin); d = d.plusDays(1)) {
            final LocalDate dia = d;
            long ocupadas = reservas.stream()
                    .filter(r -> r.getEstado() != EstadoReserva.CANCELADA)
                    .filter(r -> idsOperativas.contains(r.getUnidad().getId()))
                    .filter(r -> !dia.isBefore(r.getFechaInicio()) && !dia.isAfter(r.getFechaFin()))
                    .map(r -> r.getUnidad().getId())
                    .distinct()
                    .count();
            int libres = reservable ? (int) Math.max(0, idsOperativas.size() - ocupadas) : 0;
            dias.add(new DiaDisponibilidad(dia, libres));
        }
        return new Disponibilidad(productoId, inicio, fin, idsOperativas.size(), reservable, dias);
    }

    public List<CategoriaResponse> categorias(boolean incluirInactivas) {
        Map<UUID, Long> conteos = aMapa(categoriaRepository.contarProductosAprobados());
        List<Categoria> lista = incluirInactivas
                ? categoriaRepository.findAllByOrderByNombreAsc()
                : categoriaRepository.findByActivaTrueOrderByNombreAsc();
        return lista.stream()
                .map(c -> new CategoriaResponse(c.getId(), c.getNombre(), c.getDescripcion(), c.isActiva(),
                        conteos.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public List<Ref> marcas() {
        return marcaRepository.findAllByOrderByNombreAsc().stream().map(Ref::de).toList();
    }

    private Specification<Producto> especificacion(Filtros f, LocalDate desde, LocalDate hasta) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("estadoPublicacion"), EstadoPublicacion.APROBADO));

            if (f.q() != null && !f.q().isBlank()) {
                var marca = root.join("marca", JoinType.LEFT);
                var categoria = root.join("categoria", JoinType.LEFT);
                List<Predicate> porPalabra = new ArrayList<>();
                for (String palabra : f.q().trim().toLowerCase().split("\\s+")) {
                    String like = "%" + palabra + "%";
                    porPalabra.add(cb.or(
                            cb.like(cb.lower(root.get("nombre")), like),
                            cb.like(cb.lower(root.get("descripcion")), like),
                            cb.like(cb.lower(marca.get("nombre")), like),
                            cb.like(cb.lower(categoria.get("nombre")), like)));
                }
                ps.add(cb.and(porPalabra.toArray(Predicate[]::new)));
            }
            if (f.categoriaId() != null) {
                ps.add(cb.equal(root.get("categoria").get("id"), f.categoriaId()));
            }
            if (f.marcaIds() != null && !f.marcaIds().isEmpty()) {
                ps.add(root.get("marca").get("id").in(f.marcaIds()));
            }
            if (f.precioMin() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("tarifaDia"), f.precioMin()));
            }
            if (f.precioMax() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("tarifaDia"), f.precioMax()));
            }
            if (f.calificacionMin() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("calificacionPromedio"), f.calificacionMin()));
            }
            if (desde != null) {
                // Existe al menos una unidad operativa sin reservas activas solapadas en [desde, hasta].
                Subquery<UUID> libre = query.subquery(UUID.class);
                var u = libre.from(UnidadProducto.class);

                Subquery<UUID> ocupada = libre.subquery(UUID.class);
                var r = ocupada.from(Reserva.class);
                ocupada.select(r.get("id")).where(
                        cb.equal(r.get("unidad"), u),
                        cb.notEqual(r.get("estado"), EstadoReserva.CANCELADA),
                        cb.lessThanOrEqualTo(r.get("fechaInicio"), hasta),
                        cb.greaterThanOrEqualTo(r.get("fechaFin"), desde));

                libre.select(u.get("id")).where(
                        cb.equal(u.get("producto"), root),
                        cb.equal(u.get("estado"), EstadoUnidad.DISPONIBLE),
                        cb.not(cb.exists(ocupada)));

                ps.add(cb.exists(libre));
                ps.add(cb.notEqual(root.get("estadoFisico"), EstadoFisico.EN_MANTENIMIENTO));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
    }

    private Sort orden(String orden) {
        if (orden == null) {
            orden = "relevancia";
        }
        return switch (orden) {
            case "precio_asc" -> Sort.by(Sort.Order.asc("tarifaDia"), Sort.Order.asc("nombre"));
            case "precio_desc" -> Sort.by(Sort.Order.desc("tarifaDia"), Sort.Order.asc("nombre"));
            case "calificacion" -> Sort.by(Sort.Order.desc("calificacionPromedio"), Sort.Order.desc("totalCalificaciones"));
            case "recientes" -> Sort.by(Sort.Order.desc("creadoEn"));
            case "nombre" -> Sort.by(Sort.Order.asc("nombre"));
            default -> Sort.by(Sort.Order.desc("totalCalificaciones"), Sort.Order.desc("calificacionPromedio"),
                    Sort.Order.asc("nombre"));
        };
    }

    private static Map<UUID, Long> aMapa(List<Object[]> filas) {
        Map<UUID, Long> mapa = new HashMap<>();
        for (Object[] fila : filas) {
            mapa.put((UUID) fila[0], ((Number) fila[1]).longValue());
        }
        return mapa;
    }
}
