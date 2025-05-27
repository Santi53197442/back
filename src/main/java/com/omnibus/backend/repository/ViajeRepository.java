// src/main/java/com/omnibus/backend/repository/ViajeRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ViajeRepository extends JpaRepository<Viaje, Integer> {

    // MÉTODO MODIFICADO/ANTIGUO DEL USUARIO (lo mantengo pero no lo usaré para la lógica de inactividad)
    // Este método es limitado porque solo considera una única 'fecha' y no maneja bien
    // períodos de inactividad que abarcan múltiples días o viajes que cruzan medianoche.
    List<Viaje> findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoIn(
            Omnibus bus,
            LocalDate fecha,
            LocalTime nuevaHoraLlegada, // Esto parece ser horaFinViaje
            LocalTime nuevaHoraSalida,  // Esto parece ser horaInicioViaje
            List<EstadoViaje> estados
    );

    // NUEVO MÉTODO (MÁS ROBUSTO PARA LA VERIFICACIÓN DE INACTIVIDAD)
    // Busca viajes para un bus, dentro de un rango de fechas, y con ciertos estados.
    // El filtrado fino por LocalDateTime se hará en el servicio.
    @Query("SELECT v FROM Viaje v WHERE v.busAsignado.id = :omnibusId " +
            "AND v.estado IN :estadosConsiderados " +
            "AND v.fecha >= :fechaDesde AND v.fecha <= :fechaHasta")
    List<Viaje> findPotentialOverlappingTripsInDateRange(
            @Param("omnibusId") Long omnibusId,
            @Param("estadosConsiderados") List<EstadoViaje> estadosConsiderados,
            @Param("fechaDesde") LocalDate fechaDesde,
            @Param("fechaHasta") LocalDate fechaHasta
    );


    // MÉTODOS EXISTENTES DEL USUARIO (para otras funcionalidades)
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

    // NUEVO: Método para encontrar viajes de un bus por su ID y ciertos estados
    // Útil para otras verificaciones, como al intentar asignar un bus a un nuevo viaje.
    List<Viaje> findByBusAsignado_IdAndEstadoIn(Long busId, List<EstadoViaje> estados);
}