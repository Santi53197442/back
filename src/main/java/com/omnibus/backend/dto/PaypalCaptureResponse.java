package com.omnibus.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaypalCaptureResponse {
    private String id;
    private String status;
    private Payer payer;

    @Data
    @NoArgsConstructor
    public static class Payer {
        @JsonProperty("name")
        private PayerName name;

        @JsonProperty("email_address")
        private String emailAddress;
    }

    @Data
    @NoArgsConstructor
    public static class PayerName {
        @JsonProperty("given_name")
        private String givenName;

        @JsonProperty("surname")
        private String surname;
    }
}