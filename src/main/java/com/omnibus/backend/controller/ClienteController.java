// src/main/java/com/omnibus/backend/controller/ClienteController.java
package com.omnibus.backend.controller;

import com.omnibus.backend.dto.PasajeResponseDTO; // Asegúrate que este DTO exista y sea adecuado
import com.omnibus.backend.service.pasajeService; // Servicio para la lógica de pasajes

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cliente") // Nueva ruta base para funcionalidades específicas del cliente
public class ClienteController {

    private static final Logger logger = LoggerFactory.getLogger(ClienteController.class);

    private final pasajeService pasajeService;

    @Autowired
    public ClienteController(pasajeService pasajeService) {
        this.pasajeService = pasajeService;
    }

    /**
     * Obtiene el historial de pasajes comprados por un cliente específico.
     * Un cliente solo puede ver su propio historial.
     * Un vendedor o administrador puede ver el historial de cualquier cliente.
     *
     * @param clienteId El ID del cliente cuyo historial de pasajes se desea obtener.
     * @return Una lista de PasajeResponseDTO con el historial de pasajes o un error si no se encuentra.
     */
    @GetMapping("/{clienteId}/historial-pasajes")
    @PreAuthorize("(hasRole('CLIENTE') and #clienteId == principal.id) or hasAnyRole('VENDEDOR', 'ADMINISTRADOR')")
    public ResponseEntity<?> obtenerHistorialPasajesPorCliente(@PathVariable Long clienteId) {
        try {
            logger.info("API: Solicitud de historial de pasajes para el cliente ID: {}", clienteId);

            // Asumimos que pasajeService.obtenerHistorialPasajesPorClienteId(clienteId)
            // devuelve List<PasajeResponseDTO> y maneja internamente si el cliente existe
            // o si no tiene pasajes (devolviendo lista vacía en ese caso).
            // Si el cliente en sí no existe, el servicio podría lanzar EntityNotFoundException.
            List<PasajeResponseDTO> historialPasajes = pasajeService.obtenerHistorialPasajesPorClienteId(clienteId);

            if (historialPasajes.isEmpty()) {
                logger.info("API: No se encontró historial de pasajes para el cliente ID: {} (o el cliente no tiene pasajes).", clienteId);
                // Devolver una lista vacía es apropiado si el cliente existe pero no tiene pasajes.
                // Si el servicio lanza EntityNotFoundException cuando el cliente no existe, se manejará en el catch.
                return ResponseEntity.ok(Collections.emptyList());
            }

            logger.info("API: Encontrados {} pasajes en el historial del cliente ID: {}", historialPasajes.size(), clienteId);
            return ResponseEntity.ok(historialPasajes);

        } catch (EntityNotFoundException e) {
            // Esta excepción podría ser lanzada por el servicio si el clienteId no corresponde a ningún cliente.
            logger.warn("API: Cliente no encontrado al intentar obtener historial de pasajes (ID {}): {}", clienteId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("API: Error interno al obtener el historial de pasajes para el cliente ID {}: {}", clienteId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno al procesar la solicitud de historial de pasajes."));
        }
    }
}