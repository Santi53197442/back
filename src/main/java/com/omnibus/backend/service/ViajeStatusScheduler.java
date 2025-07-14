package com.omnibus.backend.service;

import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.PasajeRepository;
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
    private final PasajeRepository pasajeRepository;
    private final EmailService emailService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private FirebaseNotificationService firebaseNotificationService;

    @Autowired
    public ViajeStatusScheduler(ViajeRepository viajeRepository, OmnibusRepository omnibusRepository, PasajeRepository pasajeRepository, EmailService emailService) {
        this.viajeRepository = viajeRepository;
        this.omnibusRepository = omnibusRepository;
        this.pasajeRepository = pasajeRepository;
        this.emailService = emailService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void actualizarEstadosDeViajes() {
        LocalDateTime ahoraEnUruguay = LocalDateTime.now(ZONA_HORARIA_URUGUAY);
        logger.info("Ejecutando tarea programada. Hora actual (Uruguay): {}", ahoraEnUruguay);

        limpiarYFinalizarViajesAtascados(ahoraEnUruguay);
        cerrarVentasYNotificar(ahoraEnUruguay);
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
        // Buscamos viajes que ya pasaron su hora de salida y están en VENTAS_CERRADAS
        List<Viaje> viajesParaIniciar = viajeRepository.findTripsToStart(ahora); // ¡Necesitas modificar esta consulta!
        if (!viajesParaIniciar.isEmpty()) {
            logger.info("[!] Se encontraron {} viajes para cambiar a EN_CURSO.", viajesParaIniciar.size());
            for (Viaje viaje : viajesParaIniciar) {
                logger.info("--> Cambiando viaje ID {} de {} a EN_CURSO. Hora de salida: {}", viaje.getId(), viaje.getEstado(), viaje.getFechaHoraSalida());
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

    private void cerrarVentasYNotificar(LocalDateTime ahora) {
        // Busca viajes cuya hora de salida esté entre la hora actual y una hora en el futuro.
        LocalDateTime limiteDeCierre = ahora.plusHours(1);
        List<Viaje> viajesParaCerrarVentas = viajeRepository.findTripsToCloseSales(ahora, limiteDeCierre);

        if (!viajesParaCerrarVentas.isEmpty()) {
            logger.info("[!] Se encontraron {} viajes para CERRAR VENTAS y notificar a los pasajeros.", viajesParaCerrarVentas.size());

            for (Viaje viaje : viajesParaCerrarVentas) {
                logger.info("--> Cerrando ventas para el viaje ID {}. Hora de salida: {}", viaje.getId(), viaje.getFechaHoraSalida());
                viaje.setEstado(EstadoViaje.VENTAS_CERRADAS);

                // Obtiene todos los pasajes vendidos para este viaje específico.
                List<Pasaje> pasajesDelViaje = pasajeRepository.findByDatosViajeAndEstado(viaje, EstadoPasaje.VENDIDO);
                logger.info("...Encontrados {} pasajeros con pasajes VENDIDOS para notificar en el viaje ID {}.", pasajesDelViaje.size(), viaje.getId());

                // Itera sobre cada pasaje para notificar a cada cliente.
                for (Pasaje pasaje : pasajesDelViaje) {

                    // 1. Enviar Email
                    try {
                        emailService.sendDepartureReminderEmail(pasaje);
                    } catch (Exception e) {
                        logger.error("...[ERROR] No se pudo enviar el recordatorio por EMAIL al cliente ID {} ({}) para el viaje ID {}. Causa: {}",
                                pasaje.getCliente().getId(), pasaje.getCliente().getEmail(), viaje.getId(), e.getMessage());
                    }

                    // 2. Enviar Notificación Push (Firebase)
                    try {
                        firebaseNotificationService.sendVentasCerradasNotification(pasaje);
                        logger.info("...Notificación PUSH enviada a cliente ID {} para viaje ID {}", pasaje.getCliente().getId(), viaje.getId());
                    } catch (Exception e) {
                        logger.error("...[ERROR] No se pudo enviar notificación PUSH al cliente ID {} ({}) para el viaje ID {}. Causa: {}",
                                pasaje.getCliente().getId(), pasaje.getCliente().getEmail(), viaje.getId(), e.getMessage());
                    }

                    // 3. --- ¡NUEVA LÓGICA: CREAR NOTIFICACIÓN WEB! ---
                    try {
                        notificacionService.crearNotificacionRecordatorioViaje(pasaje);
                        logger.info("...Notificación WEB (campanita) creada para cliente ID {} para viaje ID {}", pasaje.getCliente().getId(), viaje.getId());
                    } catch (Exception e) {
                        logger.error("...[ERROR] No se pudo crear la notificación WEB para el cliente ID {} para el viaje ID {}. Causa: {}",
                                pasaje.getCliente().getId(), viaje.getId(), e.getMessage());
                    }
                }
            }
            // Guarda todos los viajes modificados en la base de datos en una sola operación.
            viajeRepository.saveAll(viajesParaCerrarVentas);
            logger.info("[!] Proceso de cierre de ventas y notificación finalizado.");
        }
        // Si no se encuentran viajes, el método simplemente termina sin hacer nada, lo cual es correcto.
    }
}