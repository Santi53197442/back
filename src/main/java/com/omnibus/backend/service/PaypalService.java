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
        // 1) Credenciales en Basic-Auth codificadas en UTF-8
        String auth = clientId + ":" + clientSecret;
        String encodedAuth = Base64.getEncoder()
                .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON)); // 2) ¡Imprescindible!

        // grant_type=client_credentials
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    baseUrl + "/v1/oauth2/token",   // Sandbox: https://api-m.sandbox.paypal.com
                    request,
                    JsonNode.class);

            JsonNode responseBody = response.getBody();
            if (responseBody != null && responseBody.hasNonNull("access_token")) {
                return responseBody.get("access_token").asText();
            }
            // 3) Si llega aquí es que PayPal respondió pero no mandó token
            throw new IllegalStateException("PayPal no devolvió access_token: " + responseBody);

        } catch (RestClientException e) {
            logger.error("Error al obtener el token de PayPal", e);
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
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        return restTemplate.postForObject(
                baseUrl + "/v2/checkout/orders/" + orderId + "/capture",
                entity,
                PaypalCaptureResponse.class
        );
    }
}