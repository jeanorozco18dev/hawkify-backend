package com.hawkify.resena;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Texto opcional sobre una calificacion; borrado siempre logico (RF-16/RF-18). */
@Entity
@Table(name = "resena")
@Getter
@Setter
@NoArgsConstructor
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "calificacion_id", nullable = false, unique = true)
    private Calificacion calificacion;

    @Column(nullable = false)
    private String texto;

    @Column(name = "estado_moderacion", nullable = false, length = 20)
    private EstadoModeracion estadoModeracion = EstadoModeracion.VISIBLE;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn = OffsetDateTime.now();

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn = OffsetDateTime.now();

    @Column(name = "eliminado_en")
    private OffsetDateTime eliminadoEn;

    @PreUpdate
    protected void alActualizar() {
        this.actualizadoEn = OffsetDateTime.now();
    }
}
