package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class ViajeStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ViajeStatusScheduler.class);

    private final ViajeRepository viajeRepository;
    private final OmnibusRepository omnibusRepository;

    @Autowired
    public ViajeStatusScheduler(ViajeRepository viajeRepository, OmnibusRepository omnibusRepository) {
        this.viajeRepository = viajeRepository;
        this.omnibusRepository = omnibusRepository;
    }

    /**
     * Tarea programada que se ejecuta cada minuto para actualizar el estado de los viajes.
     * Cron: "segundo minuto hora día-del-mes mes día-de-la-semana"
     * "0 * * * * *" significa "a los 0 segundos de cada minuto".
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void actualizarEstadosDeViajes() {
        logger.info("Ejecutando tarea programada de actualización de estados de viaje a las {}", LocalDateTime.now());

        // Obtenemos la fecha y hora actuales UNA VEZ para consistencia en la ejecución
        LocalDate fechaActual = LocalDate.now();
        LocalTime horaActual = LocalTime.now();

        // 1. Actualizar viajes de PROGRAMADO a EN_CURSO
        actualizarViajesAEnCurso(fechaActual, horaActual);

        // 2. Actualizar viajes de EN_CURSO a FINALIZADO
        actualizarViajesAFinalizado(fechaActual, horaActual);
    }

    private void actualizarViajesAEnCurso(LocalDate fechaActual, LocalTime horaActual) {
        List<Viaje> viajesParaIniciar = viajeRepository.findScheduledTripsToStart(fechaActual, horaActual);

        if (!viajesParaIniciar.isEmpty()) {
            logger.info("Se encontraron {} viajes programados para pasar a EN_CURSO.", viajesParaIniciar.size());
            for (Viaje viaje : viajesParaIniciar) {
                logger.debug("Cambiando viaje ID {} a EN_CURSO.", viaje.getId());
                viaje.setEstado(EstadoViaje.EN_CURSO);
            }
            // Guardamos todos los cambios en una sola operación de base de datos
            viajeRepository.saveAll(viajesParaIniciar);
        }
    }

    private void actualizarViajesAFinalizado(LocalDate fechaActual, LocalTime horaActual) {
        List<Viaje> viajesParaFinalizar = viajeRepository.findOngoingTripsToFinish(fechaActual, horaActual);

        if (!viajesParaFinalizar.isEmpty()) {
            logger.info("Se encontraron {} viajes en curso para pasar a FINALIZADO.", viajesParaFinalizar.size());
            for (Viaje viaje : viajesParaFinalizar) {
                logger.debug("Finalizando viaje ID {}.", viaje.getId());
                viaje.setEstado(EstadoViaje.FINALIZADO);

                // IMPORTANTE: Replicar la lógica de negocio de finalizarViaje
                Omnibus bus = viaje.getBusAsignado();
                if (bus != null) {
                    // El bus ahora se encuentra en la localidad de destino del viaje
                    bus.setLocalidadActual(viaje.getDestino());
                    // El bus queda libre y operativo para otro viaje
                    bus.setEstado(EstadoBus.OPERATIVO);
                    // Guardamos el estado actualizado del bus
                    omnibusRepository.save(bus);
                } else {
                    logger.warn("El viaje ID {} que se está finalizando no tiene un bus asignado.", viaje.getId());
                }
            }
            // Guardamos todos los cambios de los viajes
            viajeRepository.saveAll(viajesParaFinalizar);
        }
    }
}