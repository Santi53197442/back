package com.omnibus.backend.service;

import com.omnibus.backend.dto.*;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ViajeServiceTest {

    @Mock private ViajeRepository viajeRepository;
    @Mock private LocalidadRepository localidadRepository;
    @Mock private OmnibusRepository omnibusRepository;
    @Mock private PasajeRepository pasajeRepository;

    @InjectMocks private ViajeService viajeService;

    private Omnibus omnibus;
    private Localidad origen;
    private Localidad destino;
    private Viaje viaje;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        origen = Localidad.builder().id(1L).nombre("Origen").build();
        destino = Localidad.builder().id(2L).nombre("Destino").build();
        omnibus = Omnibus.builder().id(1L).matricula("ABC123").capacidadAsientos(40).localidadActual(origen).build();
        viaje = Viaje.builder()
                .id(1)
                .fechaHoraSalida(LocalDateTime.of(2025, 6, 18, 10, 0))
                .fechaHoraLlegada(LocalDateTime.of(2025, 6, 18, 12, 0))
                .origen(origen)
                .destino(destino)
                .precio(300.0)
                .estado(EstadoViaje.PROGRAMADO)
                .busAsignado(omnibus)
                .asientosDisponibles(20)
                .pasajesVendidos(0)
                .build();
    }

    @Test
    void testObtenerViajesPorEstado_devuelveLista() {
        when(viajeRepository.findByEstado(EstadoViaje.PROGRAMADO)).thenReturn(List.of(viaje));

        List<ViajeResponseDTO> result = viajeService.obtenerViajesPorEstado(EstadoViaje.PROGRAMADO);

        assertEquals(1, result.size());
        assertEquals(1, result.get(0).getId());
    }

    @Test
    void testObtenerViajesPorEstado_listaVacia() {
        when(viajeRepository.findByEstado(EstadoViaje.CANCELADO)).thenReturn(Collections.emptyList());

        List<ViajeResponseDTO> result = viajeService.obtenerViajesPorEstado(EstadoViaje.CANCELADO);

        assertTrue(result.isEmpty());
    }

    @Test
    void testObtenerDetallesViajeParaSeleccionAsientos_conDatosCorrectos() {
        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));
        when(pasajeRepository.findByDatosViajeAndEstadoIn(eq(viaje), any())).thenReturn(List.of());

        ViajeDetalleConAsientosDTO dto = viajeService.obtenerDetallesViajeParaSeleccionAsientos(1);

        assertNotNull(dto);
        assertEquals(1, dto.getId());
        assertEquals("ABC123", dto.getOmnibusMatricula());
    }

    @Test
    void testObtenerDetallesViajeParaSeleccionAsientos_errorEstadoInvalido() {
        viaje.setEstado(EstadoViaje.FINALIZADO);
        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));

        Exception ex = assertThrows(IllegalStateException.class,
                () -> viajeService.obtenerDetallesViajeParaSeleccionAsientos(1));

        assertTrue(ex.getMessage().contains("No se pueden seleccionar asientos"));
    }

    @Test
    void testObtenerDetallesViajeParaSeleccionAsientos_sinBusAsignado() {
        viaje.setBusAsignado(null);
        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));

        Exception ex = assertThrows(IllegalStateException.class,
                () -> viajeService.obtenerDetallesViajeParaSeleccionAsientos(1));

        assertTrue(ex.getMessage().contains("no tiene un ómnibus asignado"));
    }

    @Test
    void testFinalizarViaje_estadoFinalizado() {
        viaje.setEstado(EstadoViaje.FINALIZADO);
        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));

        viajeService.finalizarViaje(1);

        verify(viajeRepository, never()).save(viaje);
    }

    @Test
    void testFinalizarViaje_cancelado_lanzaExcepcion() {
        viaje.setEstado(EstadoViaje.CANCELADO);
        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));

        assertThrows(IllegalStateException.class, () -> viajeService.finalizarViaje(1));
    }

    @Test
    void testFinalizarViaje_sinOmnibus_lanzaExcepcion() {
        viaje.setEstado(EstadoViaje.EN_CURSO);
        viaje.setBusAsignado(null);
        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));

        assertThrows(IllegalStateException.class, () -> viajeService.finalizarViaje(1));
    }

    @Test
    void testFinalizarViaje_estadoEnCurso_ok() {
        viaje.setEstado(EstadoViaje.EN_CURSO);
        omnibus.setLocalidadActual(origen);
        when(viajeRepository.findById(1)).thenReturn(Optional.of(viaje));

        viajeService.finalizarViaje(1);

        verify(viajeRepository).save(viaje);
        verify(omnibusRepository).save(omnibus);
        assertEquals(EstadoViaje.FINALIZADO, viaje.getEstado());
        assertEquals(destino, omnibus.getLocalidadActual());
        assertEquals(EstadoBus.OPERATIVO, omnibus.getEstado());
    }

    @Test
    void testListarTodosLosViajesConPrecio() {
        when(viajeRepository.findAll()).thenReturn(List.of(viaje));

        List<ViajePrecioDTO> lista = viajeService.listarTodosLosViajesConPrecio();

        assertEquals(1, lista.size());
        assertEquals("ABC123", lista.get(0).getMatriculaBus());
    }
}
