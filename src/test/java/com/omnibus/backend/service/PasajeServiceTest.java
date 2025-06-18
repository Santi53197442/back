package com.omnibus.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnibus.backend.dto.CompraMultiplePasajesRequestDTO;
import com.omnibus.backend.dto.CompraPasajeRequestDTO;
import com.omnibus.backend.dto.PasajeResponseDTO;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.PasajeRepository;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.repository.ViajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PasajeServiceTest {

    @Mock private PasajeRepository pasajeRepository;
    @Mock private ViajeRepository viajeRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PaypalService paypalService;
    @Mock private PrecioService precioService;

    @InjectMocks private pasajeService pasajeService;

    private Viaje viaje;
    private Usuario cliente;
    private Omnibus omnibus;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        omnibus = Omnibus.builder()
                .id(1L)
                .matricula("BUS123")
                .capacidadAsientos(50)
                .build();

        viaje = Viaje.builder()
                .id(1)
                .estado(EstadoViaje.PROGRAMADO)
                .asientosDisponibles(10)
                .precio(500.0)
                .fechaHoraSalida(LocalDateTime.now().plusDays(2))
                .fechaHoraLlegada(LocalDateTime.now().plusDays(2).plusHours(2))
                .busAsignado(omnibus)
                .origen(Localidad.builder().id(1L).nombre("Origen").build())
                .destino(Localidad.builder().id(2L).nombre("Destino").build())
                .build();

        cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNombre("Test User");
        cliente.setEmail("test@example.com");
    }

    @Test
    void testComprarPasaje_exito() {
        CompraPasajeRequestDTO dto = new CompraPasajeRequestDTO();
        dto.setViajeId(1);
        dto.setClienteId(1L);
        dto.setNumeroAsiento(5);

        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(pasajeRepository.findByDatosViajeAndNumeroAsientoAndEstadoIn(any(), eq(5), anyList())).thenReturn(Optional.empty());
        when(pasajeRepository.save(any(Pasaje.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PasajeResponseDTO response = pasajeService.comprarPasaje(dto);

        assertNotNull(response);
        assertEquals(5, response.getNumeroAsiento());
        assertEquals("Test User", response.getClienteNombre());
    }

    @Test
    void testComprarPasaje_asientoOcupado() {
        CompraPasajeRequestDTO dto = new CompraPasajeRequestDTO();
        dto.setViajeId(1);
        dto.setClienteId(1L);
        dto.setNumeroAsiento(5);

        Pasaje existente = new Pasaje();
        existente.setEstado(EstadoPasaje.VENDIDO);

        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(pasajeRepository.findByDatosViajeAndNumeroAsientoAndEstadoIn(any(), eq(5), anyList())).thenReturn(Optional.of(existente));

        assertThrows(IllegalStateException.class, () -> pasajeService.comprarPasaje(dto));
    }

    @Test
    void testObtenerAsientosOcupados_exito() {
        Pasaje p1 = new Pasaje();
        p1.setNumeroAsiento(5);
        p1.setEstado(EstadoPasaje.VENDIDO);

        when(viajeRepository.existsById(1)).thenReturn(true);
        when(pasajeRepository.findByDatosViajeId(1)).thenReturn(List.of(p1));

        List<Integer> asientos = pasajeService.obtenerAsientosOcupados(1);

        assertEquals(1, asientos.size());
        assertTrue(asientos.contains(5));
    }

    @Test
    void testObtenerHistorialPasajesPorClienteId_existe() {
        Pasaje p1 = new Pasaje();
        p1.setId(1);
        p1.setCliente(cliente);
        p1.setDatosViaje(viaje);
        p1.setNumeroAsiento(10);
        p1.setEstado(EstadoPasaje.VENDIDO);

        when(usuarioRepository.existsById(1L)).thenReturn(true);
        when(pasajeRepository.findByClienteId(1L)).thenReturn(List.of(p1));

        List<PasajeResponseDTO> result = pasajeService.obtenerHistorialPasajesPorClienteId(1L);

        assertEquals(1, result.size());
        assertEquals(10, result.get(0).getNumeroAsiento());
    }

    @Test
    void testObtenerHistorialPasajesPorClienteId_noExiste() {
        when(usuarioRepository.existsById(99L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> pasajeService.obtenerHistorialPasajesPorClienteId(99L));
    }

    @Test
    void testObtenerPasajePorId_exito() {
        Pasaje pasaje = new Pasaje();
        pasaje.setId(99);
        pasaje.setCliente(cliente);
        pasaje.setDatosViaje(viaje);

        when(pasajeRepository.findById(99)).thenReturn(Optional.of(pasaje));

        PasajeResponseDTO dto = pasajeService.obtenerPasajePorId(99);

        assertNotNull(dto);
        assertEquals(99, dto.getId());
        assertEquals("Test User", dto.getClienteNombre());
    }

    @Test
    void testObtenerPasajePorId_noExiste() {
        when(pasajeRepository.findById(100)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> pasajeService.obtenerPasajePorId(100));
    }

    @Test
    void testProcesarDevolucionPasaje_exito() {
        Pasaje pasaje = new Pasaje();
        pasaje.setId(123);
        pasaje.setEstado(EstadoPasaje.VENDIDO);
        pasaje.setPaypalTransactionId("trx-001");
        pasaje.setPrecio(1000.0);
        pasaje.setCliente(cliente);
        pasaje.setDatosViaje(viaje);

        ObjectNode refundResponse = new ObjectMapper().createObjectNode();
        refundResponse.put("status", "COMPLETED");
        refundResponse.put("id", "refund-001");

        when(pasajeRepository.findById(123)).thenReturn(Optional.of(pasaje));
        when(paypalService.refundPayment("trx-001", 900.0)).thenReturn(refundResponse);
        when(pasajeRepository.save(any())).thenReturn(pasaje);
        when(viajeRepository.save(any())).thenReturn(viaje);

        String mensaje = pasajeService.procesarDevolucionPasaje(123);

        assertTrue(mensaje.contains("Devolución procesada con éxito"));
    }

    @Test
    void testProcesarDevolucionPasaje_sinPaypalId() {
        Pasaje pasaje = new Pasaje();
        pasaje.setId(456);
        pasaje.setEstado(EstadoPasaje.VENDIDO);
        pasaje.setDatosViaje(viaje);

        when(pasajeRepository.findById(456)).thenReturn(Optional.of(pasaje));

        assertThrows(IllegalStateException.class, () -> pasajeService.procesarDevolucionPasaje(456));
    }


}
