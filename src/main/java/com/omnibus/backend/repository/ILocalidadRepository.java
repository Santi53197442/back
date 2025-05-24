package com.omnibus.backend.repository;

import com.omnibus.backend.model.Localidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ILocalidadRepository extends JpaRepository<Localidad, Long> {

    Optional<Localidad> findByNombre(String nombre);

    // Para verificar si un conjunto de nombres ya existe, útil para optimizar la carga CSV
    List<Localidad> findByNombreIn(List<String> nombres);
}
