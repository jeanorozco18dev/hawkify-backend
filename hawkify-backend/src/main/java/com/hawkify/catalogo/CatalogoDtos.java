package com.hawkify.catalogo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.hawkify.usuario.Usuario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Contratos JSON del modulo de catalogo. */
public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    public record Ref(UUID id, String nombre) {
        static Ref de(Categoria c) {
            return c == null ? null : new Ref(c.getId(), c.getNombre());
        }

        static Ref de(Marca m) {
            return m == null ? null : new Ref(m.getId(), m.getNombre());
        }
    }

    public record PersonaRef(UUID id, String nombre, String correo) {
        static PersonaRef publica(Usuario u) {
            return u == null ? null : new PersonaRef(u.getId(), u.getNombrePublico(), null);
        }

        static PersonaRef completa(Usuario u) {
            return u == null ? null : new PersonaRef(u.getId(), u.getNombreCompleto(), u.getCorreo());
        }
    }

    /** Tarjeta de catalogo (RF-06). */
    public record ProductoResumen(
            UUID id,
            String codigo,
            String nombre,
            Ref categoria,
            Ref marca,
            BigDecimal tarifaDia,
            BigDecimal calificacionPromedio,
            int totalCalificaciones,
            String imagen,
            EstadoFisico estadoFisico,
            boolean disponibleHoy,
            long unidadesLibresHoy,
            long unidadesOperativas,
            boolean enListaDeseos,
            String propietario
    ) {
    }

    public record ImagenDto(String url, boolean principal) {
    }

    public record EspecificacionDto(
            @NotBlank(message = "Cada especificación necesita un nombre") @Size(max = 80) String clave,
            @NotBlank(message = "Cada especificación necesita un valor") @Size(max = 200) String valor) {
    }

    /** Ficha detallada (RF-08). */
    public record ProductoDetalle(
            UUID id,
            String codigo,
            String nombre,
            Ref categoria,
            Ref marca,
            String descripcion,
            String condicionesUso,
            String politicaGarantia,
            boolean garantiaPorDefecto,
            BigDecimal tarifaDia,
            BigDecimal calificacionPromedio,
            int totalCalificaciones,
            EstadoPublicacion estadoPublicacion,
            EstadoFisico estadoFisico,
            String motivoRechazo,
            boolean reservable,
            long unidadesOperativas,
            long unidadesLibresHoy,
            List<ImagenDto> imagenes,
            List<EspecificacionDto> especificaciones,
            PersonaRef propietario,
            OffsetDateTime propietarioDesde,
            boolean esPropio,
            boolean enListaDeseos,
            OffsetDateTime creadoEn
    ) {
    }

    public record DiaDisponibilidad(LocalDate fecha, int disponibles) {
    }

    /** Calendario de disponibilidad (RF-22). */
    public record Disponibilidad(
            UUID productoId,
            LocalDate desde,
            LocalDate hasta,
            int unidadesOperativas,
            boolean reservable,
            List<DiaDisponibilidad> dias
    ) {
    }

    public record CategoriaResponse(UUID id, String nombre, String descripcion, boolean activa, long productos) {
    }

    /** Alta/edicion de ficha (RF-09, RF-10). */
    public record ProductoRequest(
            @NotBlank(message = "El nombre es obligatorio")
            @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
            String nombre,

            @NotNull(message = "Selecciona una categoría")
            UUID categoriaId,

            @Size(max = 100, message = "La marca no puede superar 100 caracteres")
            String marca,

            @NotBlank(message = "Describe la herramienta: para que sirve y que incluye")
            @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres")
            String descripcion,

            @Size(max = 4000, message = "Las condiciones de uso no pueden superar 4000 caracteres")
            String condicionesUso,

            @Size(max = 4000, message = "La política de garantía no puede superar 4000 caracteres")
            String politicaGarantia,

            @NotNull(message = "La tarifa por día es obligatoria")
            @DecimalMin(value = "1000", message = "La tarifa mínima es $1.000 por día")
            @DecimalMax(value = "5000000", message = "La tarifa máxima es $5.000.000 por día")
            BigDecimal tarifaDia,

            @NotNull(message = "Indica el estado físico de la herramienta")
            EstadoFisico estadoFisico,

            @NotNull(message = "Indica cuantas unidades tienes disponibles")
            @Min(value = 1, message = "Debe haber al menos 1 unidad")
            @Max(value = 50, message = "Máximo 50 unidades por ficha")
            Integer unidades,

            @Size(max = 8, message = "Máximo 8 imágenes por herramienta")
            List<@NotBlank String> imagenes,

            @Size(max = 20, message = "Máximo 20 especificaciones")
            List<@Valid EspecificacionDto> especificaciones
    ) {
    }

    /** Vista de gestion (propietario y staff). */
    public record ProductoGestion(
            UUID id,
            String codigo,
            String nombre,
            Ref categoria,
            Ref marca,
            BigDecimal tarifaDia,
            EstadoPublicacion estadoPublicacion,
            EstadoFisico estadoFisico,
            String motivoRechazo,
            String imagen,
            int unidadesTotales,
            long unidadesOperativas,
            BigDecimal calificacionPromedio,
            int totalCalificaciones,
            PersonaRef propietario,
            PersonaRef revisor,
            OffsetDateTime creadoEn,
            OffsetDateTime actualizadoEn
    ) {
        public static ProductoGestion de(Producto p) {
            long operativas = p.getUnidades().stream().filter(u -> u.getEstado() == EstadoUnidad.DISPONIBLE).count();
            return new ProductoGestion(p.getId(), p.getCodigo(), p.getNombre(), Ref.de(p.getCategoria()),
                    Ref.de(p.getMarca()), p.getTarifaDia(), p.getEstadoPublicacion(), p.getEstadoFisico(),
                    p.getMotivoRechazo(), p.getImagenPrincipal(), p.getUnidades().size(), operativas,
                    p.getCalificacionPromedio(), p.getTotalCalificaciones(),
                    PersonaRef.completa(p.getPropietario()), PersonaRef.completa(p.getAdministradorRevisor()),
                    p.getCreadoEn(), p.getActualizadoEn());
        }
    }

    public record UnidadResponse(UUID id, String codigoInterno, EstadoUnidad estado, OffsetDateTime creadoEn) {
        public static UnidadResponse de(UnidadProducto u) {
            return new UnidadResponse(u.getId(), u.getCodigoInterno(), u.getEstado(), u.getCreadoEn());
        }
    }
}
