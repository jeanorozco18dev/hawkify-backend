package com.hawkify.reserva;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RF-26: acta de devolucion, 1:1 con la reserva. */
@Entity
@Table(name = "devolucion")
@Getter
@Setter
@NoArgsConstructor
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, unique = true)
    private Reserva reserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registrado_por", nullable = false)
    private Usuario registradoPor;

    @Column(name = "fecha_real_devolucion", nullable = false)
    private OffsetDateTime fechaRealDevolucion = OffsetDateTime.now();

    @Column(name = "estado_equipo", nullable = false, length = 20)
    private EstadoEquipo estadoEquipo;

    private String observaciones;
}
