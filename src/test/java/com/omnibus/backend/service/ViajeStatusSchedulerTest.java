package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Localidad;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ViajeStatusSchedulerTest {

    @Mock private ViajeRepository viajeRepository;
    @Mock private OmnibusRepository omnibusRepository;
    @InjectMocks private ViajeStatusScheduler scheduler;

    @Test
    void actualizarEstadosDeViajes_noViajes_nuncaGuarda() {
        when(viajeRepository.findScheduledTripsToFinishDirectly(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findScheduledTripsToStart(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findOngoingTripsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.actualizarEstadosDeViajes();

        verify(viajeRepository, never()).saveAll(anyList());
        verify(omnibusRepository, never()).save(any(Omnibus.class));
    }

    @Test
    void actualizarEstadosDeViajes_limpiaYFinalizaAtascados() {
        Viaje viaje = new Viaje();
        viaje.setId(10);
        viaje.setEstado(EstadoViaje.PROGRAMADO);
        viaje.setFechaHoraLlegada(LocalDateTime.now().minusHours(1));
        // Evitar NPE en getDestino().getNombre()
        Localidad destino = new Localidad();
        destino.setNombre("Montevideo");
        viaje.setDestino(destino);
        Omnibus bus = new Omnibus();
        bus.setId(5L);
        bus.setEstado(EstadoBus.FUERA_DE_SERVICIO);
        viaje.setBusAsignado(bus);

        when(viajeRepository.findScheduledTripsToFinishDirectly(any(LocalDateTime.class)))
                .thenReturn(List.of(viaje));
        when(viajeRepository.findScheduledTripsToStart(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findOngoingTripsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(omnibusRepository.findById(5L)).thenReturn(Optional.of(bus));

        scheduler.actualizarEstadosDeViajes();

        assertEquals(EstadoViaje.FINALIZADO, viaje.getEstado());
        verify(viajeRepository, times(1)).saveAll(List.of(viaje));
        assertEquals(EstadoBus.OPERATIVO, bus.getEstado());
        verify(omnibusRepository, times(1)).save(bus);
    }

    @Test
    void actualizarEstadosDeViajes_activaEnCurso() {
        Viaje viaje = new Viaje();
        viaje.setId(20);
        viaje.setEstado(EstadoViaje.PROGRAMADO);
        viaje.setFechaHoraSalida(LocalDateTime.now().minusMinutes(1));

        when(viajeRepository.findScheduledTripsToFinishDirectly(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findScheduledTripsToStart(any(LocalDateTime.class)))
                .thenReturn(List.of(viaje));
        when(viajeRepository.findOngoingTripsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.actualizarEstadosDeViajes();

        assertEquals(EstadoViaje.EN_CURSO, viaje.getEstado());
        verify(viajeRepository, times(1)).saveAll(List.of(viaje));
    }

    @Test
    void actualizarEstadosDeViajes_finalizaEnCurso() {
        Viaje viaje = new Viaje();
        viaje.setId(30);
        viaje.setEstado(EstadoViaje.EN_CURSO);
        viaje.setFechaHoraLlegada(LocalDateTime.now().minusMinutes(1));
        // Evitar NPE en destino
        Localidad destino = new Localidad();
        destino.setNombre("Salto");
        viaje.setDestino(destino);
        Omnibus bus = new Omnibus();
        bus.setId(7L);
        viaje.setBusAsignado(bus);

        when(viajeRepository.findScheduledTripsToFinishDirectly(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findScheduledTripsToStart(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findOngoingTripsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of(viaje));
        when(omnibusRepository.findById(7L)).thenReturn(Optional.of(bus));

        scheduler.actualizarEstadosDeViajes();

        assertEquals(EstadoViaje.FINALIZADO, viaje.getEstado());
        verify(viajeRepository, times(1)).saveAll(List.of(viaje));
        assertEquals(EstadoBus.OPERATIVO, bus.getEstado());
        verify(omnibusRepository, times(1)).save(bus);
    }

    @Test
    void actualizarEstadosDeViajes_busInexistente_noGuardaOmnibus() {
        Viaje viaje = new Viaje();
        viaje.setId(40);
        viaje.setEstado(EstadoViaje.EN_CURSO);
        viaje.setFechaHoraLlegada(LocalDateTime.now().minusMinutes(1));
        viaje.setDestino(new Localidad()); // evita NPE
        viaje.setBusAsignado(null);

        when(viajeRepository.findScheduledTripsToFinishDirectly(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findScheduledTripsToStart(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findOngoingTripsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of(viaje));

        scheduler.actualizarEstadosDeViajes();

        assertEquals(EstadoViaje.FINALIZADO, viaje.getEstado());
        verify(viajeRepository, times(1)).saveAll(List.of(viaje));
        verify(omnibusRepository, never()).save(any());
    }

    @Test
    void actualizarEstadosDeViajes_busNoEncontrado_manejaException() {
        Viaje viaje = new Viaje();
        viaje.setId(50);
        viaje.setEstado(EstadoViaje.EN_CURSO);
        viaje.setFechaHoraLlegada(LocalDateTime.now().minusMinutes(1));
        viaje.setDestino(new Localidad()); // evita NPE
        Omnibus bus = new Omnibus();
        bus.setId(8L);
        viaje.setBusAsignado(bus);

        when(viajeRepository.findScheduledTripsToFinishDirectly(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findScheduledTripsToStart(any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(viajeRepository.findOngoingTripsToFinish(any(LocalDateTime.class)))
                .thenReturn(List.of(viaje));
        when(omnibusRepository.findById(8L)).thenReturn(Optional.empty());

        // No debe lanzar excepción
        scheduler.actualizarEstadosDeViajes();

        assertEquals(EstadoViaje.FINALIZADO, viaje.getEstado());
        verify(viajeRepository, times(1)).saveAll(List.of(viaje));
        verify(omnibusRepository, times(1)).findById(8L);
        verify(omnibusRepository, never()).save(any());
    }
}
