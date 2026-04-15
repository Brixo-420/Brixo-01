package com.BRIXO.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Estructura JSON que devuelve el LLM para una cotización.
 * Mapea exactamente el esquema definido en el SYSTEM_PROMPT de LlmService.
 */
@Data
@NoArgsConstructor
public class CotizacionResult {

    @JsonProperty("servicio_principal")
    private String servicioPrincipal;

    @JsonProperty("materiales")
    private List<MaterialDto> materiales;

    @JsonProperty("personal")
    private List<PersonalDto> personal;

    @JsonProperty("complejidad")
    private String complejidad; // "bajo" | "medio" | "alto"
}
