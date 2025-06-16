// src/main/java/com/omnibus/backend/repository/OmnibusRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.EstadoBus; // Asegúrate de importar
import com.omnibus.backend.model.Omnibus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OmnibusRepository extends JpaRepository<Omnibus, Long> {
    Optional<Omnibus> findByMatricula(String matricula);

    // NUEVO MÉTODO
    List<Omnibus> findByEstado(EstadoBus estado);

    List<Omnibus> findByEstadoAndInicioInactividadProgramadaBefore(EstadoBus estado, LocalDateTime ahora);

    // Busca buses INACTIVOS (en mantenimiento o fuera de servicio) cuya hora de fin de inactividad ya pasó
    List<Omnibus> findByEstadoInAndFinInactividadProgramadaBefore(List<EstadoBus> estados, LocalDateTime ahora);

}