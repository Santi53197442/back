package com.omnibus.backend.service;

import com.omnibus.backend.model.Notificacion;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.NotificacionRepository;

// --- ¡AÑADIR ESTAS DOS IMPORTACIONES! ---
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// ----------------------------------------

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class NotificacionService {

    // --- ¡AÑADIR ESTA LÍNEA DE DECLARACIÓN! ---
    private static final Logger logger = LoggerFactory.getLogger(NotificacionService.class);
    // ------------------------------------------

    @Autowired
    private NotificacionRepository notificacionRepository;

    public void crearNotificacionRecordatorioViaje(Pasaje pasaje) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        String fechaHoraFormateada = pasaje.getDatosViaje().getFechaHoraSalida().format(formatter);

        String mensaje = String.format(
                "Recordatorio: Tu viaje a %s sale pronto. Fecha: %s.",
                pasaje.getDatosViaje().getDestino().getNombre(),
                fechaHoraFormateada
        );

        Notificacion notificacion = Notificacion.builder()
                .usuario(pasaje.getCliente())
                .mensaje(mensaje)
                .fechaCreacion(LocalDateTime.now())
                .leida(false)
                .link("/mis-viajes") // Puedes añadir un link si quieres
                .build();

        notificacionRepository.save(notificacion);
        // Opcional: puedes añadir un log aquí también si quieres
        // logger.info("Notificación web de recordatorio creada para usuario ID {}", pasaje.getCliente().getId());
    }

    public void crearNotificacionDevolucion(Pasaje pasaje, double montoReembolsado) {
        String montoFormateado = String.format(Locale.US, "%.2f", montoReembolsado);

        String mensaje = String.format(
                "Devolución procesada. Se reembolsó $%s por tu pasaje a %s.",
                montoFormateado,
                pasaje.getDatosViaje().getDestino().getNombre()
        );

        Notificacion notificacion = Notificacion.builder()
                .usuario(pasaje.getCliente())
                .mensaje(mensaje)
                .fechaCreacion(LocalDateTime.now())
                .leida(false)
                .link("/mis-pasajes")
                .build();

        notificacionRepository.save(notificacion);

        // ¡Ahora esta línea funcionará!
        logger.info("Notificación web de devolución creada para usuario ID {}", pasaje.getCliente().getId());
    }
}