// src/main/java/com/omnibus/backend/dto/ReasignarViajeRequestDTO.java
package com.omnibus.backend.dto;

import jakarta.validation.constraints.NotNull;

public class ReasignarViajeRequestDTO {

    @NotNull(message = "El ID del nuevo ómnibus no puede ser nulo.")
    private Long nuevoOmnibusId;

    // Getters y Setters
    public Long getNuevoOmnibusId() {
        return nuevoOmnibusId;
    }

    public void setNuevoOmnibusId(Long nuevoOmnibusId) {
        this.nuevoOmnibusId = nuevoOmnibusId;
    }
}