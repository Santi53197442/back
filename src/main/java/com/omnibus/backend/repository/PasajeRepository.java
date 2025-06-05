// src/main/java/com/omnibus/backend/repository/PasajeRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.EstadoPasaje;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PasajeRepository extends JpaRepository<Pasaje, Integer> { // ID es Integer

    /**
     * Cuenta los pasajes para un viaje específico que tienen un estado particular.
     * Usaremos esto para contar los asientos vendidos (pasajes con estado VENDIDO o RESERVADO).
     */
    long countByDatosViajeAndEstado(Viaje datosViaje, EstadoPasaje estado);

    /**
     * Cuenta los pasajes para un viaje específico que tienen alguno de los estados proporcionados.
     */
    long countByDatosViajeAndEstadoIn(Viaje datosViaje, List<EstadoPasaje> estados);

    // Podrías necesitar otros métodos, por ejemplo:
    // List<Pasaje> findByDatosViaje(Viaje datosViaje);
    // List<Pasaje> findByCliente(Usuario cliente);


    List<Pasaje> findByDatosViajeAndEstadoIn(Viaje datosViaje, List<EstadoPasaje> estados);

    List<Pasaje> findByDatosViajeId(Integer viajeId);

    Optional<Pasaje> findByDatosViajeAndNumeroAsiento(Viaje viaje, Integer numeroAsiento);

    List<Pasaje> findByClienteId(Long clienteId);
}