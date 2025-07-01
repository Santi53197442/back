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

    // --- QUERIES DE LÓGICA DE NEGOCIO (con JOIN FETCH para evitar errores LAZY) ---

    @Query("SELECT v FROM Viaje v JOIN FETCH v.origen JOIN FETCH v.destino JOIN FETCH v.busAsignado WHERE v.busAsignado = :bus " +
            "AND v.fechaHoraSalida < :finViajeNuevo " +
            "AND v.fechaHoraLlegada > :inicioViajeNuevo " +
            "AND v.estado IN :estados")
    List<Viaje> findOverlappingTrips(
            @Param("bus") Omnibus bus,
            @Param("inicioViajeNuevo") LocalDateTime inicioViajeNuevo,
            @Param("finViajeNuevo") LocalDateTime finViajeNuevo,
            @Param("estados") List<EstadoViaje> estados
    );

    @Query("SELECT v FROM Viaje v JOIN FETCH v.origen JOIN FETCH v.destino JOIN FETCH v.busAsignado WHERE v.busAsignado = :bus " +
            "AND v.id <> :idViajeExcluir " +
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

    @Query("SELECT v FROM Viaje v JOIN FETCH v.origen JOIN FETCH v.destino JOIN FETCH v.busAsignado WHERE v.busAsignado = :bus " +
            "AND v.fechaHoraLlegada < :fechaHoraReferencia " +
            "AND v.estado IN :estados " +
            "ORDER BY v.fechaHoraLlegada DESC")
    List<Viaje> findUltimoViajeActivoConcluidoAntesDe(
            @Param("bus") Omnibus bus,
            @Param("fechaHoraReferencia") LocalDateTime fechaHoraReferencia,
            @Param("estados") List<EstadoViaje> estados
    );

    @Query("SELECT v FROM Viaje v JOIN FETCH v.origen JOIN FETCH v.destino JOIN FETCH v.busAsignado WHERE v.busAsignado = :bus " +
            "AND v.fechaHoraSalida > :fechaHoraReferencia " +
            "AND v.estado IN :estados " +
            "ORDER BY v.fechaHoraSalida ASC")
    List<Viaje> findProximoViajeProgramadoComenzandoDespuesDe(
            @Param("bus") Omnibus bus,
            @Param("fechaHoraReferencia") LocalDateTime fechaHoraReferencia,
            @Param("estados") List<EstadoViaje> estados
    );

    // --- MÉTODOS PARA EL SCHEDULER (CON JOIN FETCH PARA PREVENIR ERRORES) ---

    @Query("SELECT v FROM Viaje v JOIN FETCH v.busAsignado b JOIN FETCH b.localidadActual JOIN FETCH v.origen JOIN FETCH v.destino WHERE v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO AND v.fechaHoraSalida <= :ahora")
    List<Viaje> findScheduledTripsToStart(@Param("ahora") LocalDateTime ahora);

    @Query("SELECT v FROM Viaje v JOIN FETCH v.busAsignado b JOIN FETCH b.localidadActual JOIN FETCH v.origen JOIN FETCH v.destino WHERE v.estado = com.omnibus.backend.model.EstadoViaje.EN_CURSO AND v.fechaHoraLlegada <= :ahora")
    List<Viaje> findOngoingTripsToFinish(@Param("ahora") LocalDateTime ahora);

    @Query("SELECT v FROM Viaje v JOIN FETCH v.busAsignado b JOIN FETCH b.localidadActual JOIN FETCH v.origen JOIN FETCH v.destino WHERE v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO AND v.fechaHoraLlegada <= :ahora")
    List<Viaje> findScheduledTripsToFinishDirectly(@Param("ahora") LocalDateTime ahora);

    // --- Métodos de búsqueda simple ---
    // Estos no necesitan JOIN FETCH a menos que se usen en contextos donde se accede a las relaciones.
    List<Viaje> findByEstado(EstadoViaje estado);
    List<Viaje> findByBusAsignado_Id(Long omnibusId);
    List<Viaje> findByBusAsignado_IdAndEstadoIn(Long busId, List<EstadoViaje> estados);

    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO AND v.fechaHoraSalida <= :unaHoraDespues AND v.fechaHoraSalida > :ahora")
    List<Viaje> findTripsToCloseSales(@Param("ahora") LocalDateTime ahora, @Param("unaHoraDespues") LocalDateTime unaHoraDespues);

    @Query("SELECT v FROM Viaje v WHERE v.estado = com.omnibus.backend.model.EstadoViaje.VENTAS_CERRADAS AND v.fechaHoraSalida <= :ahora")
    List<Viaje> findTripsToStart(@Param("ahora") LocalDateTime ahora);

}