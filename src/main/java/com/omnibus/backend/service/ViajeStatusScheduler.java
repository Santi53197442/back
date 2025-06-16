package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.model.EstadoViaje;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.Viaje;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void actualizarEstadosDeViajes() {
        LocalDateTime ahoraEnUruguay = LocalDateTime.now(ZONA_HORARIA_URUGUAY);
        logger.info("Ejecutando tarea programada. Hora actual (Uruguay): {}", ahoraEnUruguay);

        limpiarYFinalizarViajesAtascados(ahoraEnUruguay);
        actualizarViajesAEnCurso(ahoraEnUruguay);
        actualizarViajesAFinalizado(ahoraEnUruguay);
    }

    private void limpiarYFinalizarViajesAtascados(LocalDateTime ahora) {
        List<Viaje> viajesParaFinalizarDirecto = viajeRepository.findScheduledTripsToFinishDirectly(ahora);
        if (!viajesParaFinalizarDirecto.isEmpty()) {
            logger.warn("[!] LIMPIEZA: Se encontraron {} viajes atascados en PROGRAMADO que ya deberían haber finalizado.", viajesParaFinalizarDirecto.size());
            for (Viaje viaje : viajesParaFinalizarDirecto) {
                logger.warn("--> Finalizando directamente el viaje ID {}. Hora de llegada: {}", viaje.getId(), viaje.getFechaHoraLlegada());
                finalizarViajeYLiberarBus(viaje);
            }
            viajeRepository.saveAll(viajesParaFinalizarDirecto);
        }
    }

    private void actualizarViajesAEnCurso(LocalDateTime ahora) {
        List<Viaje> viajesParaIniciar = viajeRepository.findScheduledTripsToStart(ahora);
        if (!viajesParaIniciar.isEmpty()) {
            logger.info("[!] Se encontraron {} viajes para cambiar a EN_CURSO.", viajesParaIniciar.size());
            for (Viaje viaje : viajesParaIniciar) {
                logger.info("--> Cambiando viaje ID {} de PROGRAMADO a EN_CURSO. Hora de salida: {}", viaje.getId(), viaje.getFechaHoraSalida());
                viaje.setEstado(EstadoViaje.EN_CURSO);
            }
            viajeRepository.saveAll(viajesParaIniciar);
        }
    }

    private void actualizarViajesAFinalizado(LocalDateTime ahora) {
        List<Viaje> viajesParaFinalizar = viajeRepository.findOngoingTripsToFinish(ahora);
        if (!viajesParaFinalizar.isEmpty()) {
            logger.info("[!] Se encontraron {} viajes para cambiar a FINALIZADO.", viajesParaFinalizar.size());
            for (Viaje viaje : viajesParaFinalizar) {
                logger.info("--> Finalizando viaje ID {}. Hora de llegada: {}", viaje.getId(), viaje.getFechaHoraLlegada());
                finalizarViajeYLiberarBus(viaje);
            }
            viajeRepository.saveAll(viajesParaFinalizar);
        }
    }

    private void finalizarViajeYLiberarBus(Viaje viaje) {
        viaje.setEstado(EstadoViaje.FINALIZADO);
        Omnibus busAsignadoEnViaje = viaje.getBusAsignado();
        if (busAsignadoEnViaje == null || busAsignadoEnViaje.getId() == null) {
            logger.warn("...[ERROR] El viaje ID {} que se está finalizando no tiene un bus asignado o el bus no tiene ID. No se puede actualizar el bus.", viaje.getId());
            return;
        }

        Long busId = busAsignadoEnViaje.getId();
        try {
            Omnibus busParaActualizar = omnibusRepository.findById(busId)
                    .orElseThrow(() -> new EntityNotFoundException("Bus con ID " + busId + " no encontrado en la BD, aunque estaba asignado al viaje " + viaje.getId()));

            logger.info("...Actualizando bus {} (ID {}): estado anterior -> {}, nueva ubicación -> {}, nuevo estado -> OPERATIVO",
                    busParaActualizar.getMatricula(), busParaActualizar.getId(), busParaActualizar.getEstado(), viaje.getDestino().getNombre());

            busParaActualizar.setLocalidadActual(viaje.getDestino());
            busParaActualizar.setEstado(EstadoBus.OPERATIVO);
            omnibusRepository.save(busParaActualizar);

        } catch (EntityNotFoundException e) {
            logger.error("...[ERROR CRÍTICO] " + e.getMessage());
        }
    }
}