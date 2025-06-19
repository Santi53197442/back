package com.omnibus.backend.controller;

import com.omnibus.backend.dto.PasajeResponseDTO;
import com.omnibus.backend.service.pasajeService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ClienteControllerTest {

    @Mock
    private pasajeService pasajeService;

    @InjectMocks
    private ClienteController clienteController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testObtenerHistorialPasajesPorCliente_conPasajes() {
        Long clienteId = 1L;
        PasajeResponseDTO pasaje = new PasajeResponseDTO();
        pasaje.setId(1);
        when(pasajeService.obtenerHistorialPasajesPorClienteId(clienteId))
                .thenReturn(List.of(pasaje));

        ResponseEntity<?> response = clienteController.obtenerHistorialPasajesPorCliente(clienteId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<?> result = (List<?>) response.getBody();
        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    void testObtenerHistorialPasajesPorCliente_sinPasajes() {
        Long clienteId = 2L;
        when(pasajeService.obtenerHistorialPasajesPorClienteId(clienteId))
                .thenReturn(Collections.emptyList());

        ResponseEntity<?> response = clienteController.obtenerHistorialPasajesPorCliente(clienteId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Collections.emptyList(), response.getBody());
    }

    @Test
    void testObtenerHistorialPasajesPorCliente_clienteNoExiste() {
        Long clienteId = 3L;
        when(pasajeService.obtenerHistorialPasajesPorClienteId(clienteId))
                .thenThrow(new EntityNotFoundException("Cliente no encontrado"));

        ResponseEntity<?> response = clienteController.obtenerHistorialPasajesPorCliente(clienteId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertTrue(((String) ((java.util.Map<?, ?>) response.getBody()).get("message"))
                .contains("Cliente no encontrado"));
    }

    @Test
    void testObtenerHistorialPasajesPorCliente_errorInterno() {
        Long clienteId = 4L;
        when(pasajeService.obtenerHistorialPasajesPorClienteId(clienteId))
                .thenThrow(new RuntimeException("Fallo interno"));

        ResponseEntity<?> response = clienteController.obtenerHistorialPasajesPorCliente(clienteId);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertTrue(((String) ((java.util.Map<?, ?>) response.getBody()).get("message"))
                .contains("Error interno"));
    }
}
