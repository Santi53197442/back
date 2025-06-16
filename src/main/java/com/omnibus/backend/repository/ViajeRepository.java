// src/main/java/com/omnibus/backend/repository/ViajeRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Integer>, JpaSpecificationExecutor<Viaje> {

    /**
     * Encuentra viajes de un bus específico en una fecha dada cuyas horas se solapan con un rango horario.
     * Se usa para verificar si un bus ya está ocupado durante el horario de un nuevo viaje.
     * El viaje existente (v) se solapa con el viaje candidato si:
     * v.horaSalida < horaLlegadaViajeCandidato AND v.horaLlegada > horaSalidaViajeCandidato
     * Spring Data JPA generará la query correcta basada en el nombre del método.
     */
    List<Viaje> findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoIn(
            Omnibus bus,
            LocalDate fecha,
            LocalTime horaLlegadaViajeCandidato, // Corresponde al fin del intervalo del nuevo viaje
            LocalTime horaSalidaViajeCandidato,  // Corresponde al inicio del intervalo del nuevo viaje
            List<EstadoViaje> estados
    );

    /**
     * Similar al anterior, pero excluye un viaje específico por su ID.
     * Útil para la reasignación, para no considerar el propio viaje que se está reasignando como un conflicto.
     */
    List<Viaje> findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoInAndIdNot(
            Omnibus bus,
            LocalDate fecha,
            LocalTime horaLlegadaViajeCandidato,
            LocalTime horaSalidaViajeCandidato,
            List<EstadoViaje> estados,
            Integer idViajeExcluir
    );

    /**
     * Encuentra el último viaje activo (PROGRAMADO o EN_CURSO) de un bus específico
     * que ocurre en la misma fecha pero cuya hora de LLEGADA es ANTES de una horaReferencia (hora de SALIDA del nuevo viaje),
     * o en fechas anteriores. Se ordena por fecha y hora de llegada descendente para obtener el más reciente.
     */
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND ((v.fecha = :fechaReferencia AND v.horaLlegada < :horaReferencia) OR v.fecha < :fechaReferencia) " +
            "AND v.estado IN :estados " + // Pasar lista de estados activos
            "ORDER BY v.fecha DESC, v.horaLlegada DESC")
    List<Viaje> findUltimoViajeActivoConcluidoAntesDe( // Renombrado para claridad
                                                       @Param("bus") Omnibus bus,
                                                       @Param("fechaReferencia") LocalDate fechaReferencia, // Fecha del nuevo viaje
                                                       @Param("horaReferencia") LocalTime horaReferencia,    // Hora de SALIDA del nuevo viaje
                                                       @Param("estados") List<EstadoViaje> estados         // Ej: PROGRAMADO, EN_CURSO
    );

    /**
     * Encuentra el próximo viaje programado de un bus específico
     * que ocurre en la misma fecha pero cuya hora de SALIDA es DESPUÉS de una horaReferencia (hora de LLEGADA del nuevo viaje),
     * o en fechas posteriores. Se ordena por fecha y hora de salida ascendente para obtener el más próximo.
     */
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND ((v.fecha = :fechaReferencia AND v.horaSalida > :horaReferencia) OR v.fecha > :fechaReferencia) " +
            "AND v.estado IN :estados " + // Pasar lista de estados relevantes, ej: PROGRAMADO
            "ORDER BY v.fecha ASC, v.horaSalida ASC")
    List<Viaje> findProximoViajeProgramadoComenzandoDespuesDe( // Renombrado para claridad
                                                               @Param("bus") Omnibus bus,
                                                               @Param("fechaReferencia") LocalDate fechaReferencia, // Fecha del nuevo viaje
                                                               @Param("horaReferencia") LocalTime horaReferencia,    // Hora de LLEGADA del nuevo viaje
                                                               @Param("estados") List<EstadoViaje> estados         // Ej: PROGRAMADO
    );

    // --- Métodos que ya tenías y son útiles ---
    List<Viaje> findByEstado(EstadoViaje estado);
    List<Viaje> findByBusAsignado_Id(Long omnibusId); // Para búsqueda simple
    List<Viaje> findByBusAsignado_IdAndEstadoIn(Long busId, List<EstadoViaje> estados);


    // El método findPotentialOverlappingTripsInDateRange que tenías puede ser útil para otras cosas,
    // pero para la lógica específica de selección de bus en crearViaje, los métodos anteriores son más directos.
    // Lo mantengo por si lo usas en otro lado.
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado.id = :omnibusId " +
            "AND v.estado IN :estadosConsiderados " +
            "AND v.fecha >= :fechaDesde AND v.fecha <= :fechaHasta")
    List<Viaje> findPotentialOverlappingTripsInDateRange(
            @Param("omnibusId") Long omnibusId,
            @Param("estadosConsiderados") List<EstadoViaje> estadosConsiderados,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta
    );

    /**
     * Busca viajes que están PROGRAMADOS y cuya hora de salida ya ha pasado.
     */
    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO AND (v.fecha < :fechaActual OR (v.fecha = :fechaActual AND v.horaSalida <= :horaActual))")
    List<Viaje> findScheduledTripsToStart(@Param("fechaActual") LocalDate fechaActual, @Param("horaActual") LocalTime horaActual);

    /**
     * Busca viajes que están EN_CURSO y cuya hora de llegada ya ha pasado.
     */
    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.EN_CURSO AND (v.fecha < :fechaActual OR (v.fecha = :fechaActual AND v.horaLlegada <= :horaActual))")
    List<Viaje> findOngoingTripsToFinish(@Param("fechaActual") LocalDate fechaActual, @Param("horaActual") LocalTime horaActual);

    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO AND (v.fecha < :fechaActual OR (v.fecha = :fechaActual AND v.horaLlegada <= :horaActual))")
    List<Viaje> findScheduledTripsToFinishDirectly(@Param("fechaActual") LocalDate fechaActual, @Param("horaActual") LocalTime horaActual);

}
