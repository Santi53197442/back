// src/main/java/com/omnibus/backend/repository/PasajeRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.EstadoPasaje;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PasajeRepository extends JpaRepository<Pasaje, Integer> {

    // --- MÉTODOS EXISTENTES QUE SE MANTIENEN ---

    long countByDatosViajeAndEstado(Viaje datosViaje, EstadoPasaje estado);

    long countByDatosViajeAndEstadoIn(Viaje datosViaje, List<EstadoPasaje> estados);

    List<Pasaje> findByDatosViajeAndEstadoIn(Viaje datosViaje, List<EstadoPasaje> estados);

    List<Pasaje> findByDatosViajeId(Integer viajeId);

    List<Pasaje> findByClienteId(Long clienteId);

    List<Pasaje> findByEstadoAndFechaReservaBefore(EstadoPasaje estado, LocalDateTime fecha);


    // --- MÉTODO ANTIGUO ELIMINADO ---
    // Optional<Pasaje> findByDatosViajeAndNumeroAsiento(Viaje viaje, Integer numeroAsiento);
    // Este método fue eliminado porque causaba NonUniqueResultException al no diferenciar por estado.


    // --- NUEVOS MÉTODOS AÑADIDOS PARA REEMPLAZARLO ---

    /**
     * Busca un pasaje ACTIVO (Vendido o Reservado) para un viaje y asiento específicos.
     * Ignora los pasajes CANCELADOS, evitando la NonUniqueResultException.
     * Se usa para verificar si un asiento está realmente ocupado antes de una nueva reserva.
     *
     * @param viaje El viaje en el que se busca.
     * @param numeroAsiento El número de asiento a verificar.
     * @param estados Una lista de estados a considerar como "ocupado" (ej. [VENDIDO, RESERVADO]).
     * @return Un Optional que contiene el pasaje si se encuentra uno activo, o vacío si está libre.
     */
    Optional<Pasaje> findByDatosViajeAndNumeroAsientoAndEstadoIn(Viaje viaje, Integer numeroAsiento, List<EstadoPasaje> estados);

    /**
     * Busca un pasaje con un ESTADO ESPECÍFICO para un viaje y asiento.
     * Se usa para encontrar una reserva existente (estado RESERVADO) y confirmarla,
     * ignorando cualquier pasaje CANCELADO para el mismo asiento.
     *
     * @param viaje El viaje en el que se busca.
     * @param numeroAsiento El número de asiento.
     * @param estado El estado exacto que debe tener el pasaje (ej. RESERVADO).
     * @return Un Optional que contiene el pasaje si se encuentra, o vacío si no.
     */
    Optional<Pasaje> findByDatosViajeAndNumeroAsientoAndEstado(Viaje viaje, Integer numeroAsiento, EstadoPasaje estado);

    // --- MÉTODO NUEVO QUE DEBES AÑADIR ---
    /**
     * Cuenta la cantidad de pasajes para un viaje y cliente específicos que están en un estado determinado.
     * Es ideal para verificar el límite de reservas temporales.
     * @param viaje El objeto Viaje.
     * @param cliente El objeto Usuario (cliente).
     * @param estado El estado del pasaje a contar (ej. RESERVADO).
     * @return El número de pasajes que cumplen los criterios.
     */
    long countByDatosViajeAndClienteAndEstado(Viaje viaje, Usuario cliente, EstadoPasaje estado);

}