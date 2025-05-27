package com.omnibus.backend.repository;

import com.omnibus.backend.model.Localidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LocalidadRepository extends JpaRepository<Localidad, Long> {
    Optional<Localidad> findByNombre(String nombre);
    // Opcional: para buscar por nombre y departamento
    // Optional<Localidad> findByNombreAndDepartamento(String nombre, String departamento);
}