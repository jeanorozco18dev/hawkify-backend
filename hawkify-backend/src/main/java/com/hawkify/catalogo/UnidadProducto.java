package com.hawkify.catalogo;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ejemplar fisico de un producto. Las reservas ocupan una unidad concreta. */
@Entity
@Table(name = "unidad_producto")
@Getter
@Setter
@NoArgsConstructor
public class UnidadProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "codigo_interno", unique = true, length = 50)
    private String codigoInterno;

    @Column(nullable = false, length = 20)
    private EstadoUnidad estado = EstadoUnidad.DISPONIBLE;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    public UnidadProducto(Producto producto, String codigoInterno) {
        this.producto = producto;
        this.codigoInterno = codigoInterno;
    }
}
