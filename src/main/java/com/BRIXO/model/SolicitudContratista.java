package com.BRIXO.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "solicitudes_contratista")
@Getter
@Setter
@NoArgsConstructor
public class SolicitudContratista {

    public enum Estado {
        PENDIENTE, APROBADA, RECHAZADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado = Estado.PENDIENTE;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud = LocalDateTime.now();

    private LocalDateTime fechaRespuesta;

    @Column(name = "numero_cedula", length = 20)
    private String numeroCedula;

    @Column(name = "identidad_verificada", nullable = false)
    private boolean identidadVerificada = false;

    @Column(name = "verificacion_score")
    private Double verificacionScore;

    @Column(name = "verificacion_error", length = 500)
    private String verificacionError;

    @Column(name = "documento_path", length = 500)
    private String documentoPath;

    @Column(name = "selfie_path", length = 500)
    private String selfiePath;

    @Column(name = "motivo_rechazo", length = 1000)
    private String motivoRechazo;

    @Column(name = "area_especialidad", length = 100)
    private String areaEspecialidad;

    @Column(name = "certificado_path", length = 500)
    private String certificadoPath;

    @Column(name = "recibo_servicios_path", length = 500)
    private String reciboServiciosPath;

    @Column(name = "antecedentes_path", length = 500)
    private String antecedentesPath;
}
