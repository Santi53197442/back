package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException; // <-- AÑADIR IMPORT
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional; // <-- AÑADIR IMPORT

@Service
public class ViajeStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ViajeStatusScheduler.class);
    private static final ZoneId ZONA_HORARIA_URUGUAY = ZoneId.of("America/Montevideo");

    private final ViajeRepository viajeRepository;
    private final OmnibusRepository omnibusRepository;

    @Autowired
    public ViajeStatusScheduler(ViajeRepository viajeRepository, OmnibusRepository omnibusRepository) {
        this.viajeRepository = viajeRepository;
        this.omnibusRepository = omnibusRepository;
    }

    // El resto de los métodos (actualizarEstadosDeViajes, limpiarYFinalizarViajesAtascados, etc.)
    // se mantienen exactamente igual. Solo vamos a cambiar el método helper que hace el trabajo final.

    // ... (copia y pega aquí los métodos: actualizarEstadosDeViajes, limpiarYFinalizarViajesAtascados,
    //      actualizarViajesAEnCurso, y actualizarViajesAFinalizado de la respuesta anterior)

    // --- MÉTODO HELPER REFACTORIZADO Y ROBUSTO ---
    private void finalizarViajeYLiberarBus(Viaje viaje) {
        logger.debug("Procesando finalización para viaje ID: {}", viaje.getId());
        viaje.setEstado(EstadoViaje.FINALIZADO);

        // Paso 1: Verificar si el viaje tiene un bus asignado.
        Omnibus busAsignadoEnViaje = viaje.getBusAsignado();
        if (busAsignadoEnViaje == null || busAsignadoEnViaje.getId() == null) {
            logger.warn("...[ERROR] El viaje ID {} que se está finalizando no tiene un bus asignado o el bus no tiene ID. No se puede actualizar el bus.", viaje.getId());
            return; // Salimos del método para este viaje
        }

        Long busId = busAsignadoEnViaje.getId();
        logger.debug("...Viaje {} tiene asignado el bus con ID: {}", viaje.getId(), busId);

        try {
            // Paso 2: Obtener una instancia "fresca" del bus desde su repositorio.
            // Esto es crucial para asegurar que el contexto de persistencia la maneje correctamente.
            Omnibus busParaActualizar = omnibusRepository.findById(busId)
                    .orElseThrow(() -> new EntityNotFoundException("Bus con ID " + busId + " no encontrado en la BD, aunque estaba asignado al viaje " + viaje.getId()));

            logger.info("...Actualizando bus {} (ID {}): estado anterior -> {}, nueva ubicación -> {}, nuevo estado -> OPERATIVO",
                    busParaActualizar.getMatricula(),
                    busParaActualizar.getId(),
                    busParaActualizar.getEstado(), // Logueamos el estado ANTES del cambio
                    viaje.getDestino().getNombre());

            // Paso 3: Modificar la entidad obtenida del repositorio.
            busParaActualizar.setLocalidadActual(viaje.getDestino());
            busParaActualizar.setEstado(EstadoBus.OPERATIVO);

            // Paso 4: Guardar explícitamente. Aunque @Transactional debería hacerlo al final,
            // ser explícito a veces ayuda a resolver problemas de persistencia.
            omnibusRepository.save(busParaActualizar);
            logger.info("...[ÉXITO] Bus {} guardado con nuevo estado OPERATIVO.", busParaActualizar.getMatricula());

        } catch (EntityNotFoundException e) {
            logger.error("...[ERROR CRÍTICO] " + e.getMessage());
            // No hacemos nada más, pero el error queda registrado.
        }
    }

    // Pega aquí los otros métodos que no cambiaron
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void actualizarEstadosDeViajes() {
        LocalDateTime ahoraEnUruguay = LocalDateTime.now(ZONA_HORARIA_URUGUAY);
        LocalDate fechaActual = ahoraEnUruguay.toLocalDate();
        LocalTime horaActual = ahoraEnUruguay.toLocalTime();

        logger.info("Ejecutando tarea programada. Hora actual (Uruguay): {}", ahoraEnUruguay);
        limpiarYFinalizarViajesAtascados(fechaActual, horaActual);
        actualizarViajesAEnCurso(fechaActual, horaActual);
        actualizarViajesAFinalizado(fechaActual, horaActual);
    }

    private void limpiarYFinalizarViajesAtascados(LocalDate fechaActual, LocalTime horaActual) {
        List<Viaje> viajesParaFinalizarDirecto = viajeRepository.findScheduledTripsToFinishDirectly(fechaActual, horaActual);
        if (!viajesParaFinalizarDirecto.isEmpty()) {
            logger.warn("[!] LIMPIEZA: Se encontraron {} viajes atascados en PROGRAMADO que ya deberían haber finalizado.", viajesParaFinalizarDirecto.size());
            for (Viaje viaje : viajesParaFinalizarDirecto) {
                logger.warn("--> Finalizando directamente el viaje ID {}. Hora de llegada: {}", viaje.getId(), viaje.getHoraLlegada());
                finalizarViajeYLiberarBus(viaje);
            }
            viajeRepository.saveAll(viajesParaFinalizarDirecto);
        }
    }

    private void actualizarViajesAEnCurso(LocalDate fechaActual, LocalTime horaActual) {
        List<Viaje> viajesParaIniciar = viajeRepository.findScheduledTripsToStart(fechaActual, horaActual);
        if (!viajesParaIniciar.isEmpty()) {
            logger.info("[!] Se encontraron {} viajes para cambiar a EN_CURSO.", viajesParaIniciar.size());
            for (Viaje viaje : viajesParaIniciar) {
                LocalDateTime horaLlegadaViaje = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());
                if(horaLlegadaViaje.isBefore(LocalDateTime.now(ZONA_HORARIA_URUGUAY))) {
                    logger.warn("El viaje {} ya pasó su hora de llegada. Será finalizado en el ciclo de limpieza.", viaje.getId());
                    continue;
                }
                logger.info("--> Cambiando viaje ID {} de PROGRAMADO a EN_CURSO. Hora de salida: {}", viaje.getId(), viaje.getHoraSalida());
                viaje.setEstado(EstadoViaje.EN_CURSO);
            }
            viajeRepository.saveAll(viajesParaIniciar);
        }
    }

    private void actualizarViajesAFinalizado(LocalDate fechaActual, LocalTime horaActual) {
        List<Viaje> viajesParaFinalizar = viajeRepository.findOngoingTripsToFinish(fechaActual, horaActual);
        if (!viajesParaFinalizar.isEmpty()) {
            logger.info("[!] Se encontraron {} viajes para cambiar a FINALIZADO.", viajesParaFinalizar.size());
            for (Viaje viaje : viajesParaFinalizar) {
                logger.info("--> Finalizando viaje ID {}. Hora de llegada: {}", viaje.getId(), viaje.getHoraLlegada());
                finalizarViajeYLiberarBus(viaje);
            }
            viajeRepository.saveAll(viajesParaFinalizar);
        }
    }
}