// src/main/java/com/omnibus/backend/dto/CompraPasajeRequestDTO.java
package com.omnibus.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;

public class CompraPasajeRequestDTO {

    @NotNull(message = "El ID del viaje no puede ser nulo.")
    private Integer viajeId;

    @NotNull(message = "El ID del cliente no puede ser nulo.")
    private Long clienteId; // El ID del Usuario que será el pasajero

    @NotNull(message = "El número de asiento no puede ser nulo.")
    @Min(value = 1, message = "El número de asiento debe ser al menos 1.")
    private Integer numeroAsiento;

    // Constructor por defecto (necesario para la deserialización de JSON)
    public CompraPasajeRequestDTO() {
    }

    // Constructor con todos los campos (opcional, pero puede ser útil)
    public CompraPasajeRequestDTO(Integer viajeId, Long clienteId, Integer numeroAsiento) {
        this.viajeId = viajeId;
        this.clienteId = clienteId;
        this.numeroAsiento = numeroAsiento;
    }

    // Getters y Setters
    public Integer getViajeId() {
        return viajeId;
    }

    public void setViajeId(Integer viajeId) {
        this.viajeId = viajeId;
    }

    public Long getClienteId() {
        return clienteId;
    }

    public void setClienteId(Long clienteId) {
        this.clienteId = clienteId;
    }

    public Integer getNumeroAsiento() {
        return numeroAsiento;
    }

    public void setNumeroAsiento(Integer numeroAsiento) {
        this.numeroAsiento = numeroAsiento;
    }
}