package com.omnibus.backend.controller;

// 1. --- IMPORTACIONES NECESARIAS ---
import com.omnibus.backend.dto.CreateOrderRequest; // <-- Importante: El DTO para recibir el monto
import com.omnibus.backend.dto.PaypalCaptureResponse;
import com.omnibus.backend.dto.PaypalOrderResponse;
import com.omnibus.backend.service.PaypalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/paypal")
public class PaypalController {

    private static final Logger logger = LoggerFactory.getLogger(PaypalController.class);

    @Autowired
    private PaypalService paypalService;

    // 2. --- MÉTODO "createOrder" MODIFICADO ---
    // Ahora acepta un cuerpo de petición (@RequestBody) con el monto.
    @PostMapping("/orders")
    public ResponseEntity<PaypalOrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        logger.info("Recibida petición para crear orden de PayPal con monto: {}", request.getAmount());

        // Validación básica del monto recibido
        if (request.getAmount() == null || request.getAmount() <= 0) {
            logger.error("Monto inválido o nulo recibido: {}", request.getAmount());
            return ResponseEntity.badRequest().build();
        }

        try {
            // Pasamos el monto dinámico recibido del frontend al servicio de PayPal
            PaypalOrderResponse order = paypalService.createOrder(request.getAmount());
            logger.info("Orden de PayPal creada con éxito. ID: {}", order.getId());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            logger.error("Error al crear la orden de PayPal", e);
            // Devolvemos un estado 500 (Error Interno del Servidor)
            return ResponseEntity.status(500).build();
        }
    }

    // 3. --- MÉTODO "captureOrder" (sin cambios funcionales, pero con logging mejorado) ---
    // Este método ya estaba bien, no necesita cambios en su lógica.
    @PostMapping("/orders/{orderId}/capture")
    public ResponseEntity<PaypalCaptureResponse> captureOrder(@PathVariable String orderId) {
        logger.info("Recibida petición para capturar la orden de PayPal: {}", orderId);
        try {
            PaypalCaptureResponse captureData = paypalService.captureOrder(orderId);

            if (captureData.getStatus() != null && captureData.getStatus().equals("COMPLETED")) {
                logger.info("Pago completado con éxito para la orden: {}", orderId);
            } else {
                logger.warn("El pago para la orden {} no fue completado. Estado: {}", orderId, captureData.getStatus());
            }

            return ResponseEntity.ok(captureData);
        } catch (Exception e) {
            logger.error("Error al capturar la orden de PayPal {}", orderId, e);
            return ResponseEntity.status(500).build();
        }
    }
}