package com.omnibus.backend.model;

public enum RoleType {
    CLIENTE,
    VENDEDOR,
    ADMINISTRADOR;

    // Método para obtener el formato que usa Spring Security (ROLE_...)
    public String getAuthority() {
        return "ROLE_" + this.name();
    }
}