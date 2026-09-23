package com.hawkify.wishlist;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.hawkify.catalogo.Producto;
import com.hawkify.usuario.Usuario;

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
@Table(name = "lista_deseos_item")
@Getter
@Setter
@NoArgsConstructor
public class ListaDeseosItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "agregado_en", nullable = false, updatable = false)
    private OffsetDateTime agregadoEn = OffsetDateTime.now();

    public ListaDeseosItem(Usuario usuario, Producto producto) {
        this.usuario = usuario;
        this.producto = producto;
    }
}
