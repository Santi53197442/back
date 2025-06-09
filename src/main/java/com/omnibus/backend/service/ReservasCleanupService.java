package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoPasaje;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Viaje;
import com.omnibus.backend.repository.PasajeRepository;
import com.omnibus.backend.repository.ViajeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ReservasCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(ReservasCleanupService.class);
    private static final int MINUTOS_EXPIRACION = 10;

    @Autowired
    private PasajeRepository pasajeRepository;
    @Autowired
    private ViajeRepository viajeRepository;

    // Se ejecuta cada minuto (60000 milisegundos)
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void limpiarReservasExpiradas() {
        logger.info("--- Iniciando tarea de limpieza de reservas expiradas ---");
        LocalDateTime tiempoExpiracion = LocalDateTime.now().minusMinutes(MINUTOS_EXPIRACION);

        List<Pasaje> reservasExpiradas = pasajeRepository.findByEstadoAndFechaReservaBefore(EstadoPasaje.RESERVADO, tiempoExpiracion);

        if (reservasExpiradas.isEmpty()) {
            logger.info("No se encontraron reservas expiradas.");
            return;
        }

        logger.warn("Se encontraron {} reservas expiradas para limpiar.", reservasExpiradas.size());

        // Agrupamos los pasajes por viaje para actualizar el contador de asientos eficientemente
        Map<Viaje, Long> conteoPorViaje = reservasExpiradas.stream()
                .collect(Collectors.groupingBy(Pasaje::getDatosViaje, Collectors.counting()));

        // Eliminamos los pasajes expirados
        pasajeRepository.deleteAll(reservasExpiradas);

        // Actualizamos el contador de asientos disponibles para cada viaje afectado
        conteoPorViaje.forEach((viaje, cantidad) -> {
            logger.info("Liberando {} asientos para el viaje ID {}", cantidad, viaje.getId());
            viaje.setAsientosDisponibles(viaje.getAsientosDisponibles() + cantidad.intValue());
            viajeRepository.save(viaje);
        });

        logger.info("--- Tarea de limpieza de reservas finalizada ---");
    }
}