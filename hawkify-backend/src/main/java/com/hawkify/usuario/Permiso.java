package com.hawkify.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "permiso")
@Getter
@Setter
@NoArgsConstructor
public class Permiso {

    @Id
    private Short id;

    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    @Column(nullable = false)
    private String descripcion;
}
