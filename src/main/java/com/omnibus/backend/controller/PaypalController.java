package com.omnibus.backend.controller;

import com.omnibus.backend.dto.PaypalCaptureResponse;
import com.omnibus.backend.dto.PaypalOrderResponse;
import com.omnibus.backend.service.PaypalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/paypal") // Ruta base para todo lo relacionado con PayPal
public class PaypalController {

    @Autowired
    private PaypalService paypalService;

    // Endpoint para crear la orden
    @PostMapping("/orders")
    public ResponseEntity<PaypalOrderResponse> createOrder() {
        // En una aplicación real, el monto vendría de tu lógica de negocio (ej. del carrito de compras)
        // Por ahora, usamos un valor fijo para la prueba.
        double amount = 10.00;
        try {
            PaypalOrderResponse order = paypalService.createOrder(amount);
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            // Es buena práctica manejar errores y devolver un estado adecuado
            return ResponseEntity.status(500).build();
        }
    }

    // Endpoint para capturar la orden
    @PostMapping("/orders/{orderId}/capture")
    public ResponseEntity<PaypalCaptureResponse> captureOrder(@PathVariable String orderId) {
        try {
            PaypalCaptureResponse captureData = paypalService.captureOrder(orderId);

            // AQUÍ ES DONDE DEBES IMPLEMENTAR TU LÓGICA DE NEGOCIO:
            // 1. Verificar que captureData.getStatus() sea "COMPLETED".
            // 2. Guardar los detalles de la transacción en tu base de datos (PostgreSQL).
            // 3. Actualizar el estado del pedido en tu sistema.
            // 4. Enviar un correo de confirmación al usuario.

            return ResponseEntity.ok(captureData);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}