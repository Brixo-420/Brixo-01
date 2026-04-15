package com.BRIXO.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MaterialDto {

    @JsonProperty("nombre")
    private String nombre;

    @JsonProperty("cantidad_estimada")
    private String cantidadEstimada;
}
