package com.hawkify.usuario;

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
@Table(name = "direccion")
@Getter
@Setter
@NoArgsConstructor
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(length = 50)
    private String etiqueta;

    @Column(nullable = false, length = 200)
    private String linea1;

    @Column(length = 200)
    private String linea2;

    @Column(nullable = false, length = 100)
    private String ciudad;

    @Column(nullable = false, length = 100)
    private String departamento;

    @Column(name = "codigo_postal", length = 20)
    private String codigoPostal;

    @Column(nullable = false)
    private boolean predeterminada;

    public String comoTexto() {
        StringBuilder sb = new StringBuilder(linea1);
        if (linea2 != null && !linea2.isBlank()) {
            sb.append(", ").append(linea2);
        }
        sb.append(", ").append(ciudad).append(", ").append(departamento);
        return sb.toString();
    }
}
