package com.BRIXO.model;

public enum TipoServicio {
    PLOMERIA("Plomería"),
    CARPINTERIA("Carpintería"),
    ELECTRICIDAD("Electricidad"),
    ALBAÑILERIA("Albañilería"),
    PINTURA("Pintura"),
    JARDINERIA("Jardinería"),
    LIMPIEZA("Limpieza"),
    REPARACION_HOGAR("Reparación del Hogar"),
    MUDANZA("Mudanza"),
    INSTALACION("Instalación"),
    MANTENIMIENTO("Mantenimiento"),
    OTRO("Otro");

    private final String displayName;

    TipoServicio(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
