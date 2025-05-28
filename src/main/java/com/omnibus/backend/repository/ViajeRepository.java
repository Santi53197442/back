// src/main/java/com/omnibus/backend/repository/ViajeRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // IMPORTANTE
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Integer>, JpaSpecificationExecutor<Viaje> { // EXTENDS JpaSpecificationExecutor

    List<Viaje> findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoIn(
            Omnibus bus,
            LocalDate fecha,
            LocalTime horaLlegadaViajeCandidato,
            LocalTime horaSalidaViajeCandidato,
            List<EstadoViaje> estados
    );

    List<Viaje> findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoInAndIdNot(
            Omnibus bus,
            LocalDate fecha,
            LocalTime horaLlegadaViajeCandidato,
            LocalTime horaSalidaViajeCandidato,
            List<EstadoViaje> estados,
            Integer idViajeExcluir
    );

    @Query("SELECT v FROM Viaje v WHERE v.busAsignado.id = :omnibusId " +
            "AND v.estado IN :estadosConsiderados " +
            "AND v.fecha >= :fechaDesde AND v.fecha <= :fechaHasta")
    List<Viaje> findPotentialOverlappingTripsInDateRange(
            @Param("omnibusId") Long omnibusId,
            @Param("estadosConsiderados") List<EstadoViaje> estadosConsiderados,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta
    );

    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND ((v.fecha = :fechaReferencia AND v.horaSalida > :horaReferencia) OR v.fecha > :fechaReferencia) " +
            "AND v.estado = com.omnibus.backend.model.EstadoViaje.PROGRAMADO " +
            "ORDER BY v.fecha ASC, v.horaSalida ASC")
    List<Viaje> findProximoViajeProgramado(
            @Param("bus") Omnibus bus,
            @Param("fechaReferencia") LocalDate fechaReferencia,
            @Param("horaReferencia") LocalTime horaReferencia
    );

    @Query("SELECT v FROM Viaje v WHERE v.busAsignado = :bus " +
            "AND ((v.fecha = :fechaReferencia AND v.horaLlegada < :horaReferencia) OR v.fecha < :fechaReferencia) " +
            "AND v.estado IN (com.omnibus.backend.model.EstadoViaje.PROGRAMADO, com.omnibus.backend.model.EstadoViaje.EN_CURSO) " +
            "ORDER BY v.fecha DESC, v.horaLlegada DESC")
    List<Viaje> findUltimoViajeActivo(
            @Param("bus") Omnibus bus,
            @Param("fechaReferencia") LocalDate fechaReferencia,
            @Param("horaReferencia") LocalTime horaReferencia
    );

    List<Viaje> findByBusAsignado_IdAndEstadoIn(Long busId, List<EstadoViaje> estados);

    List<Viaje> findByEstado(EstadoViaje estado); // Para obtenerViajesPorEstado en ViajeService

    List<Viaje> findByBusAsignado_Id(Long omnibusId); // Para búsqueda simple de viajes por ómnibus
}