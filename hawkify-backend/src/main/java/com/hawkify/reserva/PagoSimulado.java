package com.hawkify.reserva;

import java.math.BigDecimal;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Pago sin pasarela real: reproduce el flujo (metodo, monto, estado, referencia). */
@Entity
@Table(name = "pago_simulado")
@Getter
@Setter
@NoArgsConstructor
public class PagoSimulado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, unique = true)
    private Reserva reserva;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(name = "metodo_simulado", nullable = false, length = 30)
    private MetodoPago metodo;

    @Column(nullable = false, length = 20)
    private EstadoPago estado = EstadoPago.PENDIENTE;

    @Column(name = "referencia_simulada", length = 60)
    private String referencia;

    /** Texto no sensible para mostrar: "Visa terminada en 4242". */
    @Column(length = 120)
    private String detalle;

    @Column(name = "fecha_pago")
    private OffsetDateTime fechaPago;
}
