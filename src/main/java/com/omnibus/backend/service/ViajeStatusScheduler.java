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
import java.time.ZoneId; // <-- ¡IMPORTANTE! AÑADIR ESTE IMPORT
import java.util.List;

@Service
public class ViajeStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ViajeStatusScheduler.class);

    // --- Definimos la Zona Horaria de Uruguay ---
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

        // --- OBTENEMOS LA HORA ACTUAL USANDO LA ZONA HORARIA CORRECTA ---
        LocalDateTime ahoraEnUruguay = LocalDateTime.now(ZONA_HORARIA_URUGUAY);
        LocalDate fechaActual = ahoraEnUruguay.toLocalDate();
        LocalTime horaActual = ahoraEnUruguay.toLocalTime();

        logger.info("Ejecutando tarea programada. Hora actual (Uruguay): {}", ahoraEnUruguay);

        // 1. Actualizar viajes de PROGRAMADO a EN_CURSO
        actualizarViajesAEnCurso(fechaActual, horaActual);

        // 2. Actualizar viajes de EN_CURSO a FINALIZADO
        actualizarViajesAFinalizado(fechaActual, horaActual);
    }

    private void actualizarViajesAEnCurso(LocalDate fechaActual, LocalTime horaActual) {
        // La consulta en el repositorio ya está bien, no necesita cambios.
        List<Viaje> viajesParaIniciar = viajeRepository.findScheduledTripsToStart(fechaActual, horaActual);

        if (!viajesParaIniciar.isEmpty()) {
            logger.info("[!] Encontrados {} viajes para cambiar a EN_CURSO.", viajesParaIniciar.size());
            for (Viaje viaje : viajesParaIniciar) {
                logger.info("--> Cambiando viaje ID {} de PROGRAMADO a EN_CURSO. Hora de salida: {}", viaje.getId(), viaje.getHoraSalida());
                viaje.setEstado(EstadoViaje.EN_CURSO);

                // Aquí también actualizamos el estado del bus a "ASIGNADO_A_VIAJE" por si acaso
                // no se hizo antes, aunque la lógica de creación de viaje ya lo hace.
                // Es una buena práctica ser defensivo.
                Omnibus bus = viaje.getBusAsignado();
                if (bus != null && bus.getEstado() != EstadoBus.ASIGNADO_A_VIAJE) {
                    logger.warn("El bus {} del viaje {} no estaba como ASIGNADO_A_VIAJE. Actualizando.", bus.getMatricula(), viaje.getId());
                    bus.setEstado(EstadoBus.ASIGNADO_A_VIAJE);
                    omnibusRepository.save(bus);
                }
            }
            viajeRepository.saveAll(viajesParaIniciar);
        }
    }

    private void actualizarViajesAFinalizado(LocalDate fechaActual, LocalTime horaActual) {
        // La consulta en el repositorio ya está bien, no necesita cambios.
        List<Viaje> viajesParaFinalizar = viajeRepository.findOngoingTripsToFinish(fechaActual, horaActual);

        if (!viajesParaFinalizar.isEmpty()) {
            logger.info("[!] Encontrados {} viajes para cambiar a FINALIZADO.", viajesParaFinalizar.size());
            for (Viaje viaje : viajesParaFinalizar) {
                logger.info("--> Finalizando viaje ID {}. Hora de llegada: {}", viaje.getId(), viaje.getHoraLlegada());
                viaje.setEstado(EstadoViaje.FINALIZADO);

                // IMPORTANTE: Aquí es donde el estado del BUS se actualiza
                Omnibus bus = viaje.getBusAsignado();
                if (bus != null) {
                    logger.info("Actualizando bus {} (ID {}): nueva ubicación -> {}, nuevo estado -> OPERATIVO",
                            bus.getMatricula(), bus.getId(), viaje.getDestino().getNombre());

                    bus.setLocalidadActual(viaje.getDestino());
                    bus.setEstado(EstadoBus.OPERATIVO); // El bus queda libre
                    omnibusRepository.save(bus);
                } else {
                    logger.warn("El viaje ID {} que se está finalizando no tiene un bus asignado.", viaje.getId());
                }
            }
            viajeRepository.saveAll(viajesParaFinalizar);
        }
    }
}