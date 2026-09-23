package com.hawkify.reserva;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.hawkify.catalogo.Producto;
import com.hawkify.catalogo.UnidadProducto;
import com.hawkify.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reserva")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unidad_producto_id", nullable = false)
    private UnidadProducto unidad;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    /** Columna GENERATED en la base: solo lectura desde JPA. */
    @Column(name = "dias", insertable = false, updatable = false)
    private Integer diasGenerados;

    @Column(name = "tarifa_dia_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal tarifaDiaSnapshot;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "costo_envio", nullable = false, precision = 12, scale = 2)
    private BigDecimal costoEnvio = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "modalidad_entrega", nullable = false, length = 20)
    private ModalidadEntrega modalidadEntrega = ModalidadEntrega.RECOGIDA;

    @Column(name = "direccion_entrega")
    private String direccionEntrega;

    @Column(nullable = false, length = 20)
    private EstadoReserva estado = EstadoReserva.PENDIENTE_PAGO;

    @Column(name = "motivo_cancelacion")
    private String motivoCancelacion;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @Column(name = "cancelado_en")
    private OffsetDateTime canceladoEn;

    @PrePersist
    protected void alCrear() {
        OffsetDateTime ahora = OffsetDateTime.now();
        if (this.creadoEn == null) {
            this.creadoEn = ahora;
        }
        this.actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        this.actualizadoEn = OffsetDateTime.now();
    }

    public int getDias() {
        return (int) ChronoUnit.DAYS.between(fechaInicio, fechaFin) + 1;
    }

    public Producto getProducto() {
        return unidad.getProducto();
    }

    public String getCodigo() {
        return "R-" + id.toString().substring(0, 8).toUpperCase();
    }
}
