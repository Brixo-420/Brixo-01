package com.BRIXO.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Resena que un participante del servicio deja al otro una vez el trabajo
 * quedo CERRADO. La restriccion unica (servicio, autor) garantiza una sola
 * resena por persona en cada servicio: el cliente califica al contratista y
 * el contratista califica al cliente.
 */
@Entity
@Table(name = "resenas", uniqueConstraints = @UniqueConstraint(
        name = "uk_resena_servicio_autor", columnNames = {"servicio_id", "autor_id"}))
@Getter
@Setter
@NoArgsConstructor
public class Resena {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destinatario_id", nullable = false)
    private Usuario destinatario;

    @NotNull(message = "La calificacion es obligatoria")
    @Min(value = 1, message = "La calificacion minima es 1 estrella")
    @Max(value = 5, message = "La calificacion maxima es 5 estrellas")
    @Column(nullable = false)
    private Integer calificacion;

    @Size(max = 500, message = "El comentario no puede superar 500 caracteres")
    @Column(length = 500)
    private String comentario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoResena tipo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_edicion")
    private LocalDateTime fechaEdicion;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
