package com.BRIXO.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "Correo invalido")
    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @JsonIgnore
    @Column(nullable = false, length = 120)
    private String password;

    @ManyToOne
    @JoinColumn(name = "rol_id")
    private Rol rol;

    /** Coordenadas para el mapa: "lat,lng" (ej. "4.7110,-74.0721"). Opcional. */
    @Column(length = 50)
    private String ubicacionMapa;
}
