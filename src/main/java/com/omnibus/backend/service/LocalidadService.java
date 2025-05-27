// src/main/java/com/omnibus/backend/service/LocalidadService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.CreateLocalidadDTO;
import com.omnibus.backend.model.Localidad;
import com.omnibus.backend.repository.LocalidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List; // <--- AÑADE ESTE IMPORT si no está
import java.util.Optional; // Para el ejemplo de obtenerPorId

@Service
public class LocalidadService { // Es una CLASE

    private final LocalidadRepository localidadRepository;

    @Autowired
    public LocalidadService(LocalidadRepository localidadRepository) {
        this.localidadRepository = localidadRepository;
    }

    @Transactional
    public Localidad crearLocalidad(CreateLocalidadDTO createLocalidadDTO) {
        String nombreNormalizado = createLocalidadDTO.getNombre().trim();
        if (localidadRepository.findByNombre(nombreNormalizado).isPresent()) {
            throw new IllegalArgumentException("La localidad con el nombre '" + nombreNormalizado + "' ya existe.");
        }

        Localidad nuevaLocalidad = new Localidad();
        nuevaLocalidad.setNombre(nombreNormalizado);
        nuevaLocalidad.setDepartamento(createLocalidadDTO.getDepartamento().trim());
        nuevaLocalidad.setDireccion(createLocalidadDTO.getDireccion().trim());

        return localidadRepository.save(nuevaLocalidad);
    }

    // --- MÉTODO NECESARIO PARA LISTAR TODAS LAS LOCALIDADES ---
    /**
     * Obtiene una lista de todas las localidades existentes.
     * @return Lista de objetos Localidad.
     */
    public List<Localidad> obtenerTodasLasLocalidades() {
        return localidadRepository.findAll(); // JpaRepository ya provee este método
    }
    // ------------------------------------------------------

    // Ejemplo de otro método que podrías tener (opcional)
    public Optional<Localidad> obtenerLocalidadPorId(Long id) {
        return localidadRepository.findById(id);
    }

    // Puedes añadir más métodos aquí según tus necesidades
}