package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoPasaje;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Viaje;
import com.omnibus.backend.repository.PasajeRepository;
import com.omnibus.backend.repository.ViajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ReservasCleanupServiceTest {

    @Mock
    private PasajeRepository pasajeRepository;
    @Mock
    private ViajeRepository viajeRepository;
    @InjectMocks
    private ReservasCleanupService service;

    @BeforeEach
    void setUp() {
        // No necesitamos nada extra aquí
    }

    @Test
    void limpiarReservasExpiradas_noReservas_nuncaEliminaNiActualiza() {
        when(pasajeRepository.findByEstadoAndFechaReservaBefore(
                eq(EstadoPasaje.RESERVADO), any(LocalDateTime.class)))
                .thenReturn(List.of());

        service.limpiarReservasExpiradas();

        verify(pasajeRepository, never()).deleteAll(anyList());
        verify(viajeRepository, never()).save(any(Viaje.class));
    }

    @Test
    void limpiarReservasExpiradas_conReservas_expiraYPueblaAsientos() {
        // Creamos dos viajes
        Viaje viaje1 = new Viaje();
        viaje1.setId(1);
        viaje1.setAsientosDisponibles(10);
        Viaje viaje2 = new Viaje();
        viaje2.setId(2);
        viaje2.setAsientosDisponibles(5);

        // Pasajes expirados para cada viaje
        Pasaje p1 = new Pasaje();
        p1.setDatosViaje(viaje1);
        p1.setEstado(EstadoPasaje.RESERVADO);
        p1.setFechaReserva(LocalDateTime.now().minusMinutes(15));

        Pasaje p2 = new Pasaje();
        p2.setDatosViaje(viaje1);
        p2.setEstado(EstadoPasaje.RESERVADO);
        p2.setFechaReserva(LocalDateTime.now().minusMinutes(20));

        Pasaje p3 = new Pasaje();
        p3.setDatosViaje(viaje2);
        p3.setEstado(EstadoPasaje.RESERVADO);
        p3.setFechaReserva(LocalDateTime.now().minusMinutes(12));

        List<Pasaje> expirados = List.of(p1, p2, p3);

        when(pasajeRepository.findByEstadoAndFechaReservaBefore(
                eq(EstadoPasaje.RESERVADO), any(LocalDateTime.class)))
                .thenReturn(expirados);

        service.limpiarReservasExpiradas();

        // Verifica que se eliminaron las reservas expiradas
        verify(pasajeRepository, times(1)).deleteAll(expirados);

        // Después de limpiar, los asientos disponibles deben haberse incrementado
        // viaje1: 10 + 2 = 12
        assertEquals(12, viaje1.getAsientosDisponibles());
        // viaje2: 5 + 1 = 6
        assertEquals(6, viaje2.getAsientosDisponibles());

        // Verifica que se guardaron ambos viajes
        verify(viajeRepository, times(1)).save(viaje1);
        verify(viajeRepository, times(1)).save(viaje2);
    }
}
