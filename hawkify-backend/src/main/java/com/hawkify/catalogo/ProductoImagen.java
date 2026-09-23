package com.hawkify.catalogo;

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

@Entity
@Table(name = "producto_imagen")
@Getter
@Setter
@NoArgsConstructor
public class ProductoImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private short orden;

    @Column(name = "es_principal", nullable = false)
    private boolean esPrincipal;

    public ProductoImagen(Producto producto, String url, short orden, boolean esPrincipal) {
        this.producto = producto;
        this.url = url;
        this.orden = orden;
        this.esPrincipal = esPrincipal;
    }
}
