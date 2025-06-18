package com.omnibus.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnibus.backend.dto.PaypalOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PayPalServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private PaypalService paypalService;

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ReflectionTestUtils.setField(paypalService, "clientId", "test-client-id");
        ReflectionTestUtils.setField(paypalService, "clientSecret", "test-client-secret");
        ReflectionTestUtils.setField(paypalService, "baseUrl", "https://api.sandbox.paypal.com");
        ReflectionTestUtils.setField(paypalService, "restTemplate", restTemplate);
    }

    @Test
    void testGetAccessToken_success() {
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        tokenResponse.put("access_token", "mock-access-token");

        ResponseEntity<JsonNode> responseEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class))).thenReturn(responseEntity);

        String token = ReflectionTestUtils.invokeMethod(paypalService, "getAccessToken");
        assertEquals("mock-access-token", token);
    }

    @Test
    void testGetAccessToken_failure() {
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        ResponseEntity<JsonNode> responseEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(anyString(), any(), eq(JsonNode.class))).thenReturn(responseEntity);

        RuntimeException exception = assertThrows(RuntimeException.class, () ->
                ReflectionTestUtils.invokeMethod(paypalService, "getAccessToken"));
        assertTrue(exception.getMessage().contains("No se pudo obtener el token"));
    }

    @Test
    void testCreateOrder_success() {
        // Mock token
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        tokenResponse.put("access_token", "mock-access-token");
        ResponseEntity<JsonNode> tokenEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(contains("oauth2"), any(), eq(JsonNode.class))).thenReturn(tokenEntity);

        PaypalOrderResponse mockOrderResponse = new PaypalOrderResponse();
        when(restTemplate.postForObject(contains("checkout/orders"), any(), eq(PaypalOrderResponse.class))).thenReturn(mockOrderResponse);

        PaypalOrderResponse response = paypalService.createOrder(123.45);
        assertNotNull(response);
    }

    @Test
    void testCaptureOrder_success() {
        // Mock token
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        tokenResponse.put("access_token", "mock-access-token");
        ResponseEntity<JsonNode> tokenEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(contains("oauth2"), any(), eq(JsonNode.class))).thenReturn(tokenEntity);

        JsonNode captureResponse = objectMapper.createObjectNode().put("status", "COMPLETED");
        when(restTemplate.postForObject(contains("capture"), any(), eq(JsonNode.class))).thenReturn(captureResponse);

        JsonNode response = paypalService.captureOrder("ORDER123");
        assertEquals("COMPLETED", response.get("status").asText());
    }

    @Test
    void testRefundPayment_success() {
        // Mock token
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        tokenResponse.put("access_token", "mock-access-token");
        ResponseEntity<JsonNode> tokenEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(contains("oauth2"), any(), eq(JsonNode.class))).thenReturn(tokenEntity);

        JsonNode refundResponse = objectMapper.createObjectNode().put("status", "COMPLETED");
        when(restTemplate.postForObject(contains("refund"), any(), eq(JsonNode.class))).thenReturn(refundResponse);

        JsonNode response = paypalService.refundPayment("CAPTURE123", 25.50);
        assertEquals("COMPLETED", response.get("status").asText());
    }

    @Test
    void testCreateOrder_failure() {
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        tokenResponse.put("access_token", "mock-access-token");
        ResponseEntity<JsonNode> tokenEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(contains("oauth2"), any(), eq(JsonNode.class))).thenReturn(tokenEntity);

        when(restTemplate.postForObject(contains("checkout/orders"), any(), eq(PaypalOrderResponse.class)))
                .thenThrow(new RuntimeException("Simulated failure"));

        assertThrows(RuntimeException.class, () -> paypalService.createOrder(10.0));
    }

    @Test
    void testRefundPayment_failure() {
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        tokenResponse.put("access_token", "mock-access-token");
        ResponseEntity<JsonNode> tokenEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(contains("oauth2"), any(), eq(JsonNode.class))).thenReturn(tokenEntity);

        when(restTemplate.postForObject(contains("refund"), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Refund failed"));

        assertThrows(RuntimeException.class, () -> paypalService.refundPayment("CAPTURE123", 10.0));
    }

    @Test
    void testCaptureOrder_failure() {
        ObjectNode tokenResponse = objectMapper.createObjectNode();
        tokenResponse.put("access_token", "mock-access-token");
        ResponseEntity<JsonNode> tokenEntity = new ResponseEntity<>(tokenResponse, HttpStatus.OK);
        when(restTemplate.postForEntity(contains("oauth2"), any(), eq(JsonNode.class))).thenReturn(tokenEntity);

        when(restTemplate.postForObject(contains("capture"), any(), eq(JsonNode.class)))
                .thenThrow(new RuntimeException("Capture failed"));

        assertThrows(RuntimeException.class, () -> paypalService.captureOrder("ORDER123"));
    }
}
