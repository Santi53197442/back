// PaypalControllerTest.java
package com.omnibus.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnibus.backend.dto.CreateOrderRequest;
import com.omnibus.backend.dto.PaypalOrderResponse;
import com.omnibus.backend.service.PaypalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PayPalControllerTest {

    @Mock private PaypalService paypalService;

    @InjectMocks private PaypalController paypalController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateOrderSuccess() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAmount(50.0);

        PaypalOrderResponse responseMock = new PaypalOrderResponse();
        responseMock.setId("ORDER123");

        when(paypalService.createOrder(50.0)).thenReturn(responseMock);

        ResponseEntity<PaypalOrderResponse> response = paypalController.createOrder(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("ORDER123", response.getBody().getId());
    }

    @Test
    void testCreateOrderInvalidAmount() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAmount(-5.0);

        ResponseEntity<PaypalOrderResponse> response = paypalController.createOrder(request);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testCreateOrderThrowsException() {
        CreateOrderRequest request = new CreateOrderRequest();
        request.setAmount(100.0);

        when(paypalService.createOrder(100.0)).thenThrow(new RuntimeException("Error"));

        ResponseEntity<PaypalOrderResponse> response = paypalController.createOrder(request);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testCaptureOrderSuccessCompleted() {
        String orderId = "ORDER123";
        ObjectNode jsonMock = mock(ObjectNode.class);
        when(jsonMock.has("status")).thenReturn(true);
        when(jsonMock.get("status").asText()).thenReturn("COMPLETED");

        when(paypalService.captureOrder(orderId)).thenReturn(jsonMock);

        ResponseEntity<JsonNode> response = paypalController.captureOrder(orderId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jsonMock, response.getBody());
    }

    @Test
    void testCaptureOrderSuccessNotCompleted() {
        String orderId = "ORDER456";
        ObjectNode jsonMock = mock(ObjectNode.class);
        when(jsonMock.has("status")).thenReturn(true);
        when(jsonMock.get("status").asText()).thenReturn("PENDING");

        when(paypalService.captureOrder(orderId)).thenReturn(jsonMock);

        ResponseEntity<JsonNode> response = paypalController.captureOrder(orderId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jsonMock, response.getBody());
    }

    @Test
    void testCaptureOrderThrowsException() {
        String orderId = "ORDER789";
        when(paypalService.captureOrder(orderId)).thenThrow(new RuntimeException("Failed"));

        ResponseEntity<JsonNode> response = paypalController.captureOrder(orderId);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }
}
