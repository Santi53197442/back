package com.omnibus.backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaypalOrderResponse {
    private String id;
    private String status;
    private String intent;
    
    // ✅ SOLO la estructura estándar de PayPal
    private List<PaypalLink> links;

    // ✅ Clase interna para representar cada link de PayPal
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaypalLink {
        private String href;
        private String rel;
        private String method;
    }
}