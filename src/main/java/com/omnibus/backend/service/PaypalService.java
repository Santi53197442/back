package com.omnibus.backend.service;

import com.omnibus.backend.dto.PaypalCaptureResponse;
import com.omnibus.backend.dto.PaypalOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
public class PaypalService {

    private static final Logger logger = LoggerFactory.getLogger(PaypalService.class);

    @Value("${paypal.client-id}")
    private String clientId;

    @Value("${paypal.client-secret}")
    private String clientSecret;

    @Value("${paypal.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Genera un token de acceso OAuth2 para autenticar las llamadas a la API de PayPal.
     * @return El token de acceso como String.
     */
    private String getAccessToken() {
        String auth = clientId + ":" + clientSecret;
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response = restTemplate.postForEntity(
                    baseUrl + "/v1/oauth2/token", entity, com.fasterxml.jackson.databind.JsonNode.class);

            if (response.getBody() != null && response.getBody().has("access_token")) {
                return response.getBody().get("access_token").asText();
            } else {
                throw new RuntimeException("No se pudo obtener el token de acceso de PayPal.");
            }
        } catch (Exception e) {
            logger.error("Error al generar el token de acceso de PayPal", e);
            throw new RuntimeException("Error al comunicarse con PayPal", e);
        }
    }

    /**
     * Crea una orden de pago en PayPal.
     * @param amount El monto total de la orden.
     * @return Un objeto PaypalOrderResponse con el ID y estado de la orden creada.
     */
    public PaypalOrderResponse createOrder(double amount) {
        String accessToken = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = String.format("""
                {
                  "intent": "CAPTURE",
                  "purchase_units": [
                    {
                      "amount": {
                        "currency_code": "USD",
                        "value": "%.2f"
                      }
                    }
                  ]
                }
                """, amount);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            return restTemplate.postForObject(baseUrl + "/v2/checkout/orders", entity, PaypalOrderResponse.class);
        } catch (Exception e) {
            logger.error("Error al crear la orden en PayPal para el monto {}", amount, e);
            throw new RuntimeException("Error al crear la orden de PayPal", e);
        }
    }

    /**
     * Captura (finaliza) el pago de una orden que ya ha sido aprobada por el usuario.
     * @param orderId El ID de la orden de PayPal a capturar.
     * @return Un objeto PaypalCaptureResponse con los detalles completos de la transacción.
     */
    public PaypalCaptureResponse captureOrder(String orderId) {
        String accessToken = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        try {
            return restTemplate.postForObject(baseUrl + "/v2/checkout/orders/" + orderId + "/capture", entity, PaypalCaptureResponse.class);
        } catch (Exception e) {
            logger.error("Error al capturar la orden {} en PayPal", orderId, e);
            throw new RuntimeException("Error al capturar el pago de PayPal", e);
        }
    }
}