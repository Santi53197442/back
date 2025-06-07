package com.omnibus.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateOrderRequest {
    private Double amount;
    // Opcional: podrías añadir la moneda si quisieras manejar más de una
    // private String currency;
}