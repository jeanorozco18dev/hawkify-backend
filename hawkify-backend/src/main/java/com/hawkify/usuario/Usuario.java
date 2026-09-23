package com.hawkify.usuario;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(length = 30)
    private String telefono;

    @Column(name = "documento_identidad", length = 30)
    private String documentoIdentidad;

    @Column(name = "foto_perfil_url")
    private String fotoPerfilUrl;

    @Column(name = "correo_verificado", nullable = false)
    @Builder.Default
    private boolean correoVerificado = false;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    /** RF-04: suspension temporal. Al vencer, el login reactiva la cuenta. */
    @Column(name = "suspendido_hasta")
    private OffsetDateTime suspendidoHasta;

    @Column(name = "motivo_estado")
    private String motivoEstado;

    /** RF-01: momento en que acepto la Politica de Tratamiento de Datos. */
    @Column(name = "acepto_politica_en")
    private OffsetDateTime aceptoPoliticaEn;

    /** Supresion de datos: la cuenta queda anonimizada, no borrada. */
    @Column(name = "eliminado_en")
    private OffsetDateTime eliminadoEn;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private OffsetDateTime creadoEn;

    @Column(name = "actualizado_en", nullable = false)
    private OffsetDateTime actualizadoEn;

    @PrePersist
    protected void alCrear() {
        OffsetDateTime ahora = OffsetDateTime.now();
        this.creadoEn = ahora;
        this.actualizadoEn = ahora;
    }

    @PreUpdate
    protected void alActualizar() {
        this.actualizadoEn = OffsetDateTime.now();
    }

    public String getNombreRol() {
        return rol.getNombre();
    }

    public boolean esSuperadmin() {
        return RolNombre.SUPERADMIN.equals(rol.getNombre());
    }

    public boolean esAdministrador() {
        return RolNombre.ADMINISTRADOR.equals(rol.getNombre());
    }

    public boolean esStaff() {
        return esSuperadmin() || esAdministrador();
    }

    /** Nombre corto para mostrar publicamente: "Laura G." */
    public String getNombrePublico() {
        String[] partes = nombreCompleto.trim().split("\\s+");
        if (partes.length == 1) {
            return partes[0];
        }
        return partes[0] + " " + partes[1].charAt(0) + ".";
    }
}
