package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.repository.OmnibusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OmnibusStatusSchedulerTest {

    @Mock
    private OmnibusRepository omnibusRepository;

    @InjectMocks
    private OmnibusStatusScheduler scheduler;

    /**
     * Caso 1: No hay buses para inactivar ni reactivar → saveAll no debe llamarse.
     */
    @Test
    void actualizarEstadosDeOmnibus_noItems_nuncaGuarda() {
        when(omnibusRepository.findByEstadoAndInicioInactividadProgramadaBefore(
                eq(EstadoBus.OPERATIVO), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(omnibusRepository.findByEstadoInAndFinInactividadProgramadaBefore(
                any(List.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.actualizarEstadosDeOmnibus();

        verify(omnibusRepository, never()).saveAll(any());
    }

    /**
     * Caso 2: Hay un bus operativo con inactividad programada → inicia inactividad.
     */
    @Test
    void actualizarEstadosDeOmnibus_iniciaInactividad() {
        Omnibus bus = new Omnibus();
        bus.setMatricula("BUS-1");
        bus.setEstado(EstadoBus.OPERATIVO);
        bus.setEstadoProgramado(EstadoBus.FUERA_DE_SERVICIO);
        bus.setInicioInactividadProgramada(LocalDateTime.now().minusMinutes(5));

        when(omnibusRepository.findByEstadoAndInicioInactividadProgramadaBefore(
                eq(EstadoBus.OPERATIVO), any(LocalDateTime.class)))
                .thenReturn(List.of(bus));
        when(omnibusRepository.findByEstadoInAndFinInactividadProgramadaBefore(
                any(List.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        scheduler.actualizarEstadosDeOmnibus();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Omnibus>> captor = ArgumentCaptor.forClass(List.class);
        verify(omnibusRepository, times(1)).saveAll(captor.capture());

        Omnibus actualizado = captor.getValue().get(0);
        assertEquals(EstadoBus.FUERA_DE_SERVICIO, actualizado.getEstado(),
                "El estado debe pasar a FUERA_DE_SERVICIO");
        assertNull(actualizado.getEstadoProgramado(),
                "Debe limpiarse el campo estadoProgramado");
        assertNull(actualizado.getInicioInactividadProgramada(),
                "Debe limpiarse la fecha de inicio de inactividad");
    }

    /**
     * Caso 3: Hay un bus inactivo cuyo fin de inactividad ya pasó → reactivar.
     */
    @Test
    void actualizarEstadosDeOmnibus_finalizaInactividad() {
        Omnibus bus = new Omnibus();
        bus.setMatricula("BUS-2");
        bus.setEstado(EstadoBus.EN_MANTENIMIENTO);
        bus.setFinInactividadProgramada(LocalDateTime.now().minusMinutes(1));

        when(omnibusRepository.findByEstadoAndInicioInactividadProgramadaBefore(
                any(EstadoBus.class), any(LocalDateTime.class)))
                .thenReturn(List.of());
        when(omnibusRepository.findByEstadoInAndFinInactividadProgramadaBefore(
                eq(List.of(EstadoBus.EN_MANTENIMIENTO, EstadoBus.FUERA_DE_SERVICIO)),
                any(LocalDateTime.class)))
                .thenReturn(List.of(bus));

        scheduler.actualizarEstadosDeOmnibus();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Omnibus>> captor = ArgumentCaptor.forClass(List.class);
        verify(omnibusRepository, times(1)).saveAll(captor.capture());

        Omnibus reactivado = captor.getValue().get(0);
        assertEquals(EstadoBus.OPERATIVO, reactivado.getEstado(),
                "El estado debe pasar a OPERATIVO");
        assertNull(reactivado.getFinInactividadProgramada(),
                "Debe limpiarse la fecha de fin de inactividad");
    }

    /**
     * Caso 4: Ambos flujos ocurren → dos llamadas a saveAll.
     */
    @Test
    void actualizarEstadosDeOmnibus_ambosFlujos() {
        Omnibus bus1 = new Omnibus();
        bus1.setMatricula("BUS-1");
        bus1.setEstado(EstadoBus.OPERATIVO);
        bus1.setEstadoProgramado(EstadoBus.FUERA_DE_SERVICIO);
        bus1.setInicioInactividadProgramada(LocalDateTime.now().minusMinutes(5));

        Omnibus bus2 = new Omnibus();
        bus2.setMatricula("BUS-2");
        bus2.setEstado(EstadoBus.FUERA_DE_SERVICIO);
        bus2.setFinInactividadProgramada(LocalDateTime.now().minusMinutes(1));

        when(omnibusRepository.findByEstadoAndInicioInactividadProgramadaBefore(
                eq(EstadoBus.OPERATIVO), any(LocalDateTime.class)))
                .thenReturn(List.of(bus1));
        when(omnibusRepository.findByEstadoInAndFinInactividadProgramadaBefore(
                eq(List.of(EstadoBus.EN_MANTENIMIENTO, EstadoBus.FUERA_DE_SERVICIO)),
                any(LocalDateTime.class)))
                .thenReturn(List.of(bus2));

        scheduler.actualizarEstadosDeOmnibus();

        // Debe llamarse dos veces: una por iniciar y otra por finalizar
        verify(omnibusRepository, times(2)).saveAll(any());
    }
}
