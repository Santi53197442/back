package com.omnibus.backend.controller;

import com.omnibus.backend.dto.*;
import com.omnibus.backend.model.*;
import com.omnibus.backend.service.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendedorControllerTest {

    @InjectMocks
    private VendedorController controller;

    @Mock
    private LocalidadService localidadService;
    @Mock
    private OmnibusService omnibusService;
    @Mock
    private ViajeService viajeService;
    @Mock
    private Validator validator;
    @Mock
    private pasajeService pasajeService;
    @Mock
    private AsyncService asyncService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testAltaLocalidadSuccess() {
        CreateLocalidadDTO dto = new CreateLocalidadDTO("Centro", "Montevideo", "Av. 18");
        Localidad loc = new Localidad();
        when(localidadService.crearLocalidad(dto)).thenReturn(loc);

        ResponseEntity<?> response = controller.altaLocalidad(dto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(loc, response.getBody());
    }

    @Test
    void testAltaLocalidadConflict() {
        CreateLocalidadDTO dto = new CreateLocalidadDTO();
        when(localidadService.crearLocalidad(dto)).thenThrow(new IllegalArgumentException("Ya existe"));

        ResponseEntity<?> response = controller.altaLocalidad(dto);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void testAltaLocalidadBatchEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "", "text/csv", new byte[0]);

        ResponseEntity<?> response = controller.altaLocalidadBatch(file);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testListarTodasLasLocalidadesParaSeleccionSuccess() {
        List<Localidad> localidades = List.of(new Localidad(), new Localidad());
        when(localidadService.obtenerTodasLasLocalidades()).thenReturn(localidades);

        ResponseEntity<List<Localidad>> response = controller.listarTodasLasLocalidadesParaSeleccion();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(localidades, response.getBody());
    }

    @Test
    void testListarTodasLasLocalidadesParaSeleccionError() {
        when(localidadService.obtenerTodasLasLocalidades()).thenThrow(new RuntimeException());

        ResponseEntity<List<Localidad>> response = controller.listarTodasLasLocalidadesParaSeleccion();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testAltaOmnibusSuccess() {
        CreateOmnibusDTO dto = new CreateOmnibusDTO();
        Omnibus omnibus = new Omnibus();
        when(omnibusService.crearOmnibus(dto)).thenReturn(omnibus);

        ResponseEntity<?> response = controller.altaOmnibus(dto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(omnibus, response.getBody());
    }

    @Test
    void testAltaOmnibusConflict() {
        CreateOmnibusDTO dto = new CreateOmnibusDTO();
        when(omnibusService.crearOmnibus(dto)).thenThrow(new IllegalArgumentException("Error"));

        ResponseEntity<?> response = controller.altaOmnibus(dto);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
    }

    @Test
    void testObtenerOmnibusPorEstadoBadRequest() {
        ResponseEntity<?> response = controller.obtenerOmnibusPorEstado("desconocido");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testAltaViajeSuccess() {
        ViajeRequestDTO dto = new ViajeRequestDTO();
        ViajeResponseDTO responseDTO = new ViajeResponseDTO();
        when(viajeService.crearViaje(dto)).thenReturn(responseDTO);

        ResponseEntity<?> response = controller.altaViaje(dto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(responseDTO, response.getBody());
    }

    @Test
    void testFinalizarViajeSuccess() {
        Integer id = 1;
        ResponseEntity<?> response = controller.finalizarViaje(id);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testObtenerViajesPorEstadoBadRequest() {
        ResponseEntity<?> response = controller.obtenerViajesPorEstado("estadoIncorrecto");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testComprarPasajeSuccess() {
        CompraPasajeRequestDTO dto = new CompraPasajeRequestDTO();
        PasajeResponseDTO resp = new PasajeResponseDTO();
        resp.setId(1);
        when(pasajeService.comprarPasaje(dto)).thenReturn(resp);

        ResponseEntity<?> response = controller.comprarPasaje(dto);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void testReservarAsientosTemporalmenteSuccess() {
        CompraMultiplePasajesRequestDTO dto = new CompraMultiplePasajesRequestDTO();
        PasajeResponseDTO pr = new PasajeResponseDTO();
        pr.setFechaReserva(LocalDateTime.now());
        when(pasajeService.reservarAsientosTemporalmente(dto)).thenReturn(List.of(pr));

        ResponseEntity<?> response = controller.reservarAsientosTemporalmente(dto);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).containsKey("expiracion"));
    }
}

