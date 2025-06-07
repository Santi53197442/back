package com.omnibus.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaypalOrderResponse {
    private String id;
    private String status;
    // Puedes añadir más campos de la respuesta de PayPal si los necesitas
}