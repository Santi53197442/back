package com.omnibus.backend.service;

import com.omnibus.backend.dto.CreateOmnibusDTO;
import com.omnibus.backend.dto.OmnibusStatsDTO;
import com.omnibus.backend.exception.BusConViajesAsignadosException;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.LocalidadRepository;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OmnibusServiceTest {

    @InjectMocks
    private OmnibusService omnibusService;

    @Mock
    private OmnibusRepository omnibusRepository;
    @Mock
    private LocalidadRepository localidadRepository;
    @Mock
    private ViajeRepository viajeRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCrearOmnibus_exitoso() {
        CreateOmnibusDTO dto = new CreateOmnibusDTO("AAA111", "Volvo", "B12", 50, EstadoBus.OPERATIVO, 1L);

        when(omnibusRepository.findByMatricula("AAA111")).thenReturn(Optional.empty());
        Localidad localidad = new Localidad();
        localidad.setId(1L);
        localidad.setNombre("Montevideo");
        when(localidadRepository.findById(1L)).thenReturn(Optional.of(localidad));

        when(omnibusRepository.save(any(Omnibus.class))).thenAnswer(i -> i.getArgument(0));

        Omnibus result = omnibusService.crearOmnibus(dto);

        assertEquals("AAA111", result.getMatricula());
        verify(omnibusRepository).save(any());
    }

    @Test
    void testObtenerTodosLosOmnibus() {
        when(omnibusRepository.findAll()).thenReturn(List.of(new Omnibus(), new Omnibus()));
        assertEquals(2, omnibusService.obtenerTodosLosOmnibus().size());
    }

    @Test
    void testObtenerOmnibusPorId_existente() {
        Omnibus o = new Omnibus();
        o.setId(1L);
        when(omnibusRepository.findById(1L)).thenReturn(Optional.of(o));
        Optional<Omnibus> result = omnibusService.obtenerOmnibusPorId(1L);
        assertTrue(result.isPresent());
    }

    @Test
    void testMarcarOmnibusInactivo_exitoso() {
        Omnibus o = new Omnibus();
        o.setId(1L);
        o.setEstado(EstadoBus.OPERATIVO);
        when(omnibusRepository.findById(1L)).thenReturn(Optional.of(o));
        when(viajeRepository.findOverlappingTrips(any(), any(), any(), any())).thenReturn(List.of());
        when(omnibusRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocalDateTime inicio = LocalDateTime.now();
        LocalDateTime fin = inicio.plusHours(2);

        Omnibus result = omnibusService.marcarOmnibusInactivo(1L, inicio, fin, EstadoBus.EN_MANTENIMIENTO);
        assertEquals(EstadoBus.EN_MANTENIMIENTO, result.getEstadoProgramado());
    }

    @Test
    void testMarcarOmnibusOperativo_exitoso() {
        Omnibus o = new Omnibus();
        o.setId(1L);
        o.setEstado(EstadoBus.FUERA_DE_SERVICIO);
        when(omnibusRepository.findById(1L)).thenReturn(Optional.of(o));
        when(omnibusRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Omnibus result = omnibusService.marcarOmnibusOperativo(1L);
        assertEquals(EstadoBus.OPERATIVO, result.getEstado());
    }

    @Test
    void testObtenerOmnibusPorEstado() {
        when(omnibusRepository.findByEstado(EstadoBus.OPERATIVO)).thenReturn(List.of(new Omnibus()));
        assertEquals(1, omnibusService.obtenerOmnibusPorEstado(EstadoBus.OPERATIVO).size());
    }

    @Test
    void testObtenerDatosParaEstadisticas() {
        Omnibus o = new Omnibus();
        o.setEstado(EstadoBus.OPERATIVO);
        o.setCapacidadAsientos(40);
        o.setMarca("Volvo");
        Localidad loc = new Localidad();
        loc.setNombre("Salto");
        o.setLocalidadActual(loc);

        when(omnibusRepository.findAll()).thenReturn(List.of(o));

        List<OmnibusStatsDTO> result = omnibusService.obtenerDatosParaEstadisticas();
        assertEquals("Salto", result.get(0).getLocalidadActualNombre());
    }

    @Test
    void testCrearOmnibus_matriculaExistente() {
        when(omnibusRepository.findByMatricula("AAA111")).thenReturn(Optional.of(new Omnibus()));
        CreateOmnibusDTO dto = new CreateOmnibusDTO("AAA111", "Volvo", "B12", 50, EstadoBus.OPERATIVO, 1L);
        assertThrows(IllegalArgumentException.class, () -> omnibusService.crearOmnibus(dto));
    }

    @Test
    void testMarcarOmnibusInactivo_conViajesAsignados() {
        Omnibus o = new Omnibus();
        o.setId(1L);
        o.setEstado(EstadoBus.OPERATIVO);

        when(omnibusRepository.findById(1L)).thenReturn(Optional.of(o));
        when(viajeRepository.findOverlappingTrips(any(), any(), any(), any())).thenReturn(List.of(new Viaje()));

        assertThrows(BusConViajesAsignadosException.class, () ->
                omnibusService.marcarOmnibusInactivo(1L, LocalDateTime.now(), LocalDateTime.now().plusHours(1), EstadoBus.FUERA_DE_SERVICIO)
        );
    }
}
