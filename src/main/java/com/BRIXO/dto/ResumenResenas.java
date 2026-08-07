package com.BRIXO.dto;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reputacion de un usuario: promedio, total y cuantas resenas hay de cada
 * cantidad de estrellas. Lo consumen las plantillas para pintar el resumen.
 */
@Getter
public class ResumenResenas {

    private final double promedio;
    private final long total;
    private final Map<Integer, Long> conteos = new LinkedHashMap<>();
    private final Map<Integer, Integer> porcentajes = new LinkedHashMap<>();

    public ResumenResenas(double promedio, long total, Map<Integer, Long> conteosPorEstrella) {
        this.promedio = Math.round(promedio * 10.0) / 10.0;
        this.total = total;

        // De 5 a 1 para que la plantilla itere en el orden en que se muestra.
        for (int estrellas = 5; estrellas >= 1; estrellas--) {
            long cantidad = conteosPorEstrella.getOrDefault(estrellas, 0L);
            conteos.put(estrellas, cantidad);
            porcentajes.put(estrellas, total == 0 ? 0 : (int) Math.round(cantidad * 100.0 / total));
        }
    }

    public static ResumenResenas vacio() {
        return new ResumenResenas(0, 0, Map.of());
    }

    public boolean isSinResenas() {
        return total == 0;
    }

    /** Promedio con un decimal: "4.5". Si no hay resenas devuelve "-". */
    public String getPromedioTexto() {
        return total == 0 ? "-" : String.format(java.util.Locale.US, "%.1f", promedio);
    }
}
