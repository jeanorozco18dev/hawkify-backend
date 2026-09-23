package com.hawkify.catalogo;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hawkify.usuario.Usuario;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrador_revisor_id")
    private Usuario administradorRevisor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marca_id")
    private Marca marca;

    @Column(nullable = false, length = 150)
    private String nombre;

    private String descripcion;

    @Column(name = "condiciones_uso")
    private String condicionesUso;

    @Column(name = "politica_garantia")
    private String politicaGarantia;

    @Column(name = "tarifa_dia", nullable = false, precision = 10, scale = 2)
    private BigDecimal tarifaDia;

    @Column(name = "estado_publicacion", nullable = false, length = 25)
    private EstadoPublicacion estadoPublicacion = EstadoPublicacion.PENDIENTE_APROBACION;

    @Column(name = "estado_fisico", nullable = false, length = 20)
    private EstadoFisico estadoFisico = EstadoFisico.NUEVO;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    /** Mantenido por el trigger fn_actualizar_calificacion_producto: JPA nunca lo escribe. */
    @Column(name = "calificacion_promedio", insertable = false, updatable = false, precision = 3, scale = 2)
    private BigDecimal calificacionPromedio;

    @Column(name = "total_calificaciones", insertable = false, updatable = false)
    private Integer totalCalificaciones;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden asc")
    private List<ProductoImagen> imagenes = new ArrayList<>();

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("clave asc")
    private List<ProductoEspecificacion> especificaciones = new ArrayList<>();

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL)
    @OrderBy("codigoInterno asc")
    private List<UnidadProducto> unidades = new ArrayList<>();

    @PrePersist
    protected void alCrear() {
        OffsetDateTime ahora = OffsetDateTime.now();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        this.actualizadoEn = OffsetDateTime.now();
    }

    public boolean esReservable() {
        return estadoPublicacion == EstadoPublicacion.APROBADO && estadoFisico != EstadoFisico.EN_MANTENIMIENTO;
    }

    public BigDecimal getCalificacionPromedio() {
        return calificacionPromedio == null ? BigDecimal.ZERO : calificacionPromedio;
    }

    public int getTotalCalificaciones() {
        return totalCalificaciones == null ? 0 : totalCalificaciones;
    }

    public String getImagenPrincipal() {
        return imagenes.stream()
                .filter(ProductoImagen::isEsPrincipal)
                .findFirst()
                .or(() -> imagenes.stream().findFirst())
                .map(ProductoImagen::getUrl)
                .orElse(null);
    }

    /** Codigo legible para la ficha tecnica: HK-7F3A2C */
    public String getCodigo() {
        return "HK-" + id.toString().substring(0, 6).toUpperCase();
    }
}
