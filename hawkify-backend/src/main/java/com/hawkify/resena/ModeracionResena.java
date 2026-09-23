package com.hawkify.resena;

import java.time.OffsetDateTime;
import java.util.UUID;

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
@Table(name = "moderacion_resena")
@Getter
@Setter
@NoArgsConstructor
public class ModeracionResena {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resena_id", nullable = false)
    private Resena resena;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderador_id", nullable = false)
    private Usuario moderador;

    @Column(nullable = false, length = 20)
    private AccionModeracion accion;

    private String motivo;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();
}
