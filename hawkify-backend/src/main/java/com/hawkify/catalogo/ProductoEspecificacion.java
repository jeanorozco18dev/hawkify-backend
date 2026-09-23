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
@Table(name = "producto_especificacion")
@Getter
@Setter
@NoArgsConstructor
public class ProductoEspecificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 80)
    private String clave;

    @Column(nullable = false, length = 200)
    private String valor;

    public ProductoEspecificacion(Producto producto, String clave, String valor) {
        this.producto = producto;
        this.clave = clave;
        this.valor = valor;
    }
}
