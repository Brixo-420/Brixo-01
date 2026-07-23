package com.BRIXO.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "validaciones_identidad")
@Getter
@Setter
@NoArgsConstructor
public class ValidacionIdentidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false)
    private boolean verificado = false;

    @Column
    private Double score;

    @Column(length = 500)
    private String error;

    @Column(name = "fecha_validacion", nullable = false)
    private LocalDateTime fechaValidacion = LocalDateTime.now();

    @Column(name = "documento_path", length = 500)
    private String documentoPath;

    @Column(name = "selfie_path", length = 500)
    private String selfiePath;

    @Column(nullable = false)
    private int intentos = 0;
}
