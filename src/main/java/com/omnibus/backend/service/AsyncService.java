package com.omnibus.backend.service;

import com.omnibus.backend.dto.PasajeResponseDTO;
import com.google.zxing.WriterException;
import com.omnibus.backend.model.Pasaje;
import jakarta.mail.MessagingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.io.IOException;

@Service
public class AsyncService {

    private static final Logger logger = LoggerFactory.getLogger(AsyncService.class);

    @Autowired
    private EmailService emailService; // Inyectamos el servicio de email original

    @Async
    public void sendTicketEmailAsync(PasajeResponseDTO pasaje) {
        logger.info("Iniciando envío asíncrono de ticket para pasaje ID: {}", pasaje.getId());
        try {
            // Llamamos al método que realmente construye y envía el email
            emailService.buildAndSendTicket(pasaje);
        } catch (Exception e) {
            logger.error("Error en la tarea asíncrona de envío de email para pasaje ID {}: {}", pasaje.getId(), e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    public void sendRefundEmailAsync(Integer pasajeId, double montoReembolsado) { // <-- CAMBIO EN LA FIRMA
        try {
            logger.info("Iniciando envío asíncrono de email de DEVOLUCIÓN para pasaje ID: {}", pasajeId);
            // Pasamos los mismos parámetros al siguiente servicio.
            emailService.sendRefundConfirmationEmail(pasajeId, montoReembolsado); // <-- CAMBIO EN LA LLAMADA
        } catch (Exception e) {
            logger.error("Error en tarea asíncrona al enviar email de DEVOLUCIÓN para pasaje ID {}: {}",
                    pasajeId, e.getMessage(), e);
        }
    }
}