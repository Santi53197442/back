package com.omnibus.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty; // <-- IMPORTACIÓN AÑADIDA
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor; // <-- IMPORTACIÓN AÑADIDA

@Data
@NoArgsConstructor
@AllArgsConstructor // <-- ANOTACIÓN AÑADIDA para generar un constructor con todos los argumentos
public class PaypalOrderResponse {
    private String id;
    private String status;

    // --- CAMBIO AÑADIDO ---
    // Este campo almacenará el enlace de aprobación que necesitamos.
    // Usamos @JsonProperty para que, si en el futuro decides mapear directamente,
    // Jackson sepa cómo manejarlo, aunque en nuestra solución lo llenaremos manualmente.
    @JsonProperty("approve_link")
    private String approveLink;
}