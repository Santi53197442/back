package com.omnibus.backend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CompraMultiplePasajesRequestDTO {
    @NotNull(message = "El ID del viaje no puede ser nulo")
    private Integer viajeId;

    @NotNull(message = "El ID del cliente no puede ser nulo")
    private Long clienteId;

    @NotEmpty(message = "La lista de asientos no puede estar vacía")
    @Size(min = 1, max = 4, message = "Solo se pueden comprar entre 1 y 4 asientos a la vez")
    private List<Integer> numerosAsiento;

    // Opcional: si quieres asociar la transacción de PayPal
    private String paypalTransactionId;
}