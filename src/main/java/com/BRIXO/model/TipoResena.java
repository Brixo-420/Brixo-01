package com.BRIXO.model;

public enum TipoResena {
    CLIENTE_A_CONTRATISTA("El cliente califica al contratista"),
    CONTRATISTA_A_CLIENTE("El contratista califica al cliente");

    private final String displayName;

    TipoResena(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
