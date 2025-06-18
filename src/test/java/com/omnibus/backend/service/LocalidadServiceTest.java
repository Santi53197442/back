package com.omnibus.backend.service;

import com.omnibus.backend.dto.CreateLocalidadDTO;
import com.omnibus.backend.model.Localidad;
import com.omnibus.backend.repository.LocalidadRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LocalidadServiceTest {

    @Mock
    private LocalidadRepository localidadRepository;

    @InjectMocks
    private LocalidadService localidadService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCrearLocalidad_exito() {
        CreateLocalidadDTO dto = new CreateLocalidadDTO();
        dto.setNombre("Nueva Localidad");
        dto.setDepartamento("Montevideo");
        dto.setDireccion("Calle Falsa 123");

        when(localidadRepository.findByNombre("Nueva Localidad")).thenReturn(Optional.empty());
        when(localidadRepository.save(any(Localidad.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Localidad result = localidadService.crearLocalidad(dto);

        assertEquals("Nueva Localidad", result.getNombre());
        assertEquals("Montevideo", result.getDepartamento());
        assertEquals("Calle Falsa 123", result.getDireccion());
    }

    @Test
    void testCrearLocalidad_nombreExistente() {
        CreateLocalidadDTO dto = new CreateLocalidadDTO();
        dto.setNombre("Existente");
        dto.setDepartamento("Montevideo");
        dto.setDireccion("Otra 123");

        when(localidadRepository.findByNombre("Existente")).thenReturn(Optional.of(new Localidad()));

        assertThrows(IllegalArgumentException.class, () -> localidadService.crearLocalidad(dto));
    }

    @Test
    void testObtenerTodasLasLocalidades() {
        Localidad localidad = new Localidad();
        localidad.setNombre("Ciudad A");

        when(localidadRepository.findAll()).thenReturn(List.of(localidad));

        List<Localidad> result = localidadService.obtenerTodasLasLocalidades();

        assertEquals(1, result.size());
        assertEquals("Ciudad A", result.get(0).getNombre());
    }

    @Test
    void testObtenerLocalidadPorId() {
        Localidad localidad = new Localidad();
        localidad.setId(10L);
        localidad.setNombre("Ciudad B");

        when(localidadRepository.findById(10L)).thenReturn(Optional.of(localidad));

        Optional<Localidad> result = localidadService.obtenerLocalidadPorId(10L);

        assertTrue(result.isPresent());
        assertEquals("Ciudad B", result.get().getNombre());
    }

    @Test
    void testObtenerLocalidadPorId_noExiste() {
        when(localidadRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<Localidad> result = localidadService.obtenerLocalidadPorId(99L);

        assertTrue(result.isEmpty());
    }
}
