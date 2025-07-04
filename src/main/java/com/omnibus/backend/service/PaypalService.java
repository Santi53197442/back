package com.omnibus.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnibus.backend.dto.PaypalOrderResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;

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
        String encodedAuth = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(body, headers);

        try {
            // Se usa JsonNode para una deserialización más robusta del token
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                    baseUrl + "/v1/oauth2/token", entity, JsonNode.class);

            if (response.getBody() != null && response.getBody().has("access_token")) {
                return response.getBody().get("access_token").asText();
            } else {
                logger.error("La respuesta de PayPal para obtener token no contiene 'access_token'. Respuesta: {}", response.getBody());
                throw new RuntimeException("No se pudo obtener el token de acceso de PayPal.");
            }
        } catch (Exception e) {
            logger.error("Error al generar el token de acceso de PayPal", e);
            throw new RuntimeException("Error al comunicarse con PayPal para obtener token", e);
        }
    }

    /**
     * Crea una orden de pago en PayPal.
     * @param amount El monto total de la orden.
     * @return Un objeto PaypalOrderResponse con el ID, estado y el ENLACE DE APROBACIÓN de la orden.
     */
    public PaypalOrderResponse createOrder(double amount) {
        String accessToken = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        // --- CAMBIO IMPORTANTE: Cuerpo de la Petición ---
        // Se añade "application_context" que es requerido por PayPal para redirigir al usuario
        // después del pago (return_url) o si lo cancela (cancel_url).
        // ¡RECUERDA CAMBIAR ESTAS URLS POR LAS DE TU APLICACIÓN REAL!
        String requestBody = String.format(Locale.US, """
                {
                  "intent": "CAPTURE",
                  "purchase_units": [
                    {
                      "amount": {
                        "currency_code": "USD",
                        "value": "%.2f"
                      }
                    }
                  ],
                  "application_context": {
                    "return_url": "https://example.com/payment-success",
                    "cancel_url": "https://example.com/payment-cancelled"
                  }
                }
                """, amount);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        try {
            logger.info("Enviando a PayPal para crear orden: {}", requestBody);

            // --- CAMBIO 1: Recibimos la respuesta como un JsonNode para tener acceso a todos los datos.
            ResponseEntity<JsonNode> responseEntity = restTemplate.postForEntity(
                    baseUrl + "/v2/checkout/orders", entity, JsonNode.class);

            JsonNode responseNode = responseEntity.getBody();
            if (responseNode == null) {
                throw new RuntimeException("La respuesta de PayPal para crear la orden fue nula.");
            }

            logger.debug("Respuesta completa de PayPal al crear orden: {}", responseNode.toString());

            // --- CAMBIO 2: Extraemos los datos que nos interesan del JsonNode.
            String orderId = responseNode.path("id").asText();
            String status = responseNode.path("status").asText();
            String approveLink = null;

            // La respuesta de PayPal contiene un array de "links". Debemos iterarlo para encontrar
            // el que tiene la relación (rel) "approve".
            if (responseNode.has("links")) {
                for (JsonNode linkNode : responseNode.get("links")) {
                    if ("approve".equals(linkNode.path("rel").asText())) {
                        approveLink = linkNode.path("href").asText();
                        break; // Encontramos el link, salimos del bucle.
                    }
                }
            }

            if (approveLink == null) {
                logger.error("No se encontró el 'approve link' en la respuesta de PayPal: {}", responseNode.toString());
                throw new RuntimeException("No se pudo obtener el enlace de aprobación de PayPal.");
            }

            // --- CAMBIO 3: Creamos y devolvemos nuestro DTO con toda la información.
            // Esto requiere que tu DTO PaypalOrderResponse tenga un constructor que acepte estos 3 argumentos.
            // Si usas Lombok, puedes añadir @AllArgsConstructor a la clase DTO.
            return new PaypalOrderResponse(orderId, status, approveLink);

        } catch (Exception e) {
            logger.error("Error al crear la orden en PayPal para el monto {}", amount, e);
            throw new RuntimeException("Error al crear la orden de PayPal", e);
        }
    }

    /**
     * Captura (finaliza) el pago de una orden que ya ha sido aprobada por el usuario.
     * @param orderId El ID de la orden de PayPal a capturar.
     * @return Un objeto JsonNode con la respuesta COMPLETA de la API de captura de PayPal.
     */
    public JsonNode captureOrder(String orderId) {
        String accessToken = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

        HttpEntity<String> entity = new HttpEntity<>(null, headers);

        try {
            logger.info("Intentando capturar orden de PayPal con ID: {}", orderId);
            return restTemplate.postForObject(
                    baseUrl + "/v2/checkout/orders/" + orderId + "/capture",
                    entity,
                    JsonNode.class
            );
        } catch (Exception e) {
            logger.error("Error al capturar la orden {} en PayPal", orderId, e);
            throw new RuntimeException("Error al capturar el pago de PayPal", e);
        }
    }

    /**
     * Realiza un reembolso de una transacción previamente capturada.
     * @param captureId El ID de la transacción de PayPal (el que guardaste como paypalTransactionId).
     * @param amountToRefund El monto a reembolsar.
     * @return Un objeto JsonNode con la respuesta del reembolso de PayPal.
     */
    public JsonNode refundPayment(String captureId, double amountToRefund) {
        String accessToken = getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        BigDecimal refundValue = BigDecimal.valueOf(amountToRefund).setScale(2, RoundingMode.HALF_UP);

        String requestBody = String.format(Locale.US, """
                {
                  "amount": {
                    "currency_code": "USD",
                    "value": "%.2f"
                  }
                }
                """, refundValue);

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
        String refundUrl = baseUrl + "/v2/payments/captures/" + captureId + "/refund";

        try {
            logger.info("Enviando a PayPal para reembolsar captura {}: {}", captureId, requestBody);
            return restTemplate.postForObject(refundUrl, entity, JsonNode.class);
        } catch (Exception e) {
            logger.error("Error al reembolsar la captura {} en PayPal", captureId, e);
            throw new RuntimeException("Error al procesar el reembolso con PayPal", e);
        }
    }
}