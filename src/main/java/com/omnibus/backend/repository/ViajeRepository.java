package com.omnibus.backend.repository;

import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Integer>, JpaSpecificationExecutor<Viaje> {

    // --- QUERIES DE LÓGICA DE NEGOCIO (ADAPTADAS A LocalDateTime) ---

    /**
     * Encuentra viajes de un bus que se solapan con un intervalo de tiempo dado.
     * Un viaje se solapa si (InicioViajeExistente < FinViajeNuevo) Y (FinViajeExistente > InicioViajeNuevo).
     * Esta es la forma más robusta de verificar conflictos de horario.
     *
     * @param bus El ómnibus a verificar.
     * @param inicioViajeNuevo El momento de inicio del viaje candidato.
     * @param finViajeNuevo El momento de fin del viaje candidato.
     * @param estados Estados de viaje a considerar como "ocupado" (ej. PROGRAMADO, EN_CURSO).
     * @return Lista de viajes conflictivos.
     */
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND v.fechaHoraSalida < :finViajeNuevo " +
            "AND v.fechaHoraLlegada > :inicioViajeNuevo " +
            "AND v.estado IN :estados")
    List<Viaje> findOverlappingTrips(
            @Param("bus") Omnibus bus,
            @Param("inicioViajeNuevo") LocalDateTime inicioViajeNuevo,
            @Param("finViajeNuevo") LocalDateTime finViajeNuevo,
            @Param("estados") List<EstadoViaje> estados
    );

    /**
     * Similar a findOverlappingTrips, pero excluye un viaje específico por su ID.
     * Esencial para la lógica de "reasignar viaje", para no marcar el propio viaje como un conflicto.
     */
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND v.id <> :idViajeExcluir " + // <-- La exclusión
            "AND v.fechaHoraSalida < :finViajeNuevo " +
            "AND v.fechaHoraLlegada > :inicioViajeNuevo " +
            "AND v.estado IN :estados")
    List<Viaje> findOverlappingTripsExcludingId(
            @Param("bus") Omnibus bus,
            @Param("inicioViajeNuevo") LocalDateTime inicioViajeNuevo,
            @Param("finViajeNuevo") LocalDateTime finViajeNuevo,
            @Param("estados") List<EstadoViaje> estados,
            @Param("idViajeExcluir") Integer idViajeExcluir
    );

    /**
     * Encuentra el último viaje activo que concluyó ANTES de una fecha/hora de referencia.
     * Útil para saber dónde estará un bus antes de un nuevo viaje.
     *
     * @param bus El ómnibus a consultar.
     * @param fechaHoraReferencia La hora de SALIDA del nuevo viaje.
     * @param estados Estados a considerar (PROGRAMADO, EN_CURSO).
     * @return Una lista que contiene (o no) el último viaje relevante. Se ordena para que el primero sea el más reciente.
     */
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND v.fechaHoraLlegada < :fechaHoraReferencia " +
            "AND v.estado IN :estados " +
            "ORDER BY v.fechaHoraLlegada DESC")
    List<Viaje> findUltimoViajeActivoConcluidoAntesDe(
            @Param("bus") Omnibus bus,
            @Param("fechaHoraReferencia") LocalDateTime fechaHoraReferencia,
            @Param("estados") List<EstadoViaje> estados
    );

    /**
     * Encuentra el próximo viaje programado que comienza DESPUÉS de una fecha/hora de referencia.
     * Útil para verificar si hay tiempo suficiente entre el fin de un viaje nuevo y el inicio del siguiente.
     *
     * @param bus El ómnibus a consultar.
     * @param fechaHoraReferencia La hora de LLEGADA del nuevo viaje.
     * @param estados Estados a considerar (normalmente solo PROGRAMADO).
     * @return Una lista que contiene (o no) el próximo viaje. Se ordena para que el primero sea el más cercano en el tiempo.
     */
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND v.fechaHoraSalida > :fechaHoraReferencia " +
            "AND v.estado IN :estados " +
            "ORDER BY v.fechaHoraSalida ASC")
    List<Viaje> findProximoViajeProgramadoComenzandoDespuesDe(
            @Param("bus") Omnibus bus,
            @Param("fechaHoraReferencia") LocalDateTime fechaHoraReferencia,
            @Param("estados") List<EstadoViaje> estados
    );


    // --- MÉTODOS PARA EL SCHEDULER (SIMPLIFICADOS Y ROBUSTOS) ---

    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO AND v.fechaHoraSalida <= :ahora")
    List<Viaje> findScheduledTripsToStart(@Param("ahora") LocalDateTime ahora);

    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.EN_CURSO AND v.fechaHoraLlegada <= :ahora")
    List<Viaje> findOngoingTripsToFinish(@Param("ahora") LocalDateTime ahora);

    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO AND v.fechaHoraLlegada <= :ahora")
    List<Viaje> findScheduledTripsToFinishDirectly(@Param("ahora") LocalDateTime ahora);


    // --- Métodos de búsqueda simple que no cambian ---
    List<Viaje> findByEstado(EstadoViaje estado);
    List<Viaje> findByBusAsignado_Id(Long omnibusId);
    List<Viaje> findByBusAsignado_IdAndEstadoIn(Long busId, List<EstadoViaje> estados);
}