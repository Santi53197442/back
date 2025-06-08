package com.omnibus.backend.service;

import com.omnibus.backend.dto.PasajeResponseDTO;
import com.google.zxing.WriterException;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final QrCodeService qrCodeService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Autowired
    public EmailService(JavaMailSender mailSender, QrCodeService qrCodeService) {
        this.mailSender = mailSender;
        this.qrCodeService = qrCodeService;
    }

    // Este método se mantiene igual
    public void sendPasswordResetEmail(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail); // Es buena práctica establecer el remitente
        message.setTo(to);
        message.setSubject("Restablecimiento de Contraseña");
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        message.setText("Hola,\n\nHas solicitado restablecer tu contraseña.\n" +
                "Haz clic en el siguiente enlace para continuar:\n" + resetUrl +
                "\n\nSi no solicitaste esto, ignora este correo.\n" +
                "El enlace expirará en 1 hora.\n\nSaludos,\nEl equipo de Omnibus");
        try {
            mailSender.send(message);
            logger.info("Correo de restablecimiento enviado a: {}", to);
        } catch (Exception e) {
            logger.error("Error al enviar correo de restablecimiento a {}: {}", to, e.getMessage());
        }
    }

    /**
     * Este método ahora es público y síncrono.
     * Su única responsabilidad es construir y enviar el correo.
     * Ya no tiene la anotación @Async.
     */
    public void buildAndSendTicket(PasajeResponseDTO pasaje) throws MessagingException, WriterException, IOException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        // El 'true' es importante para permitir contenido multipart (texto, imágenes, etc.)
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        // Generar el QR
        String qrText = "Ticket ID: " + pasaje.getId() + " | Pasajero: " + pasaje.getClienteNombre() + " | Viaje: " + pasaje.getViajeId();
        byte[] qrCode = qrCodeService.generateQrCodeImage(qrText, 250, 250);

        // Construir el cuerpo del correo con el nuevo HTML
        String htmlBody = buildTicketHtml(pasaje);

        helper.setTo(pasaje.getClienteEmail());
        helper.setFrom(fromEmail);
        helper.setSubject("✅ Tu pasaje para el viaje a " + pasaje.getDestinoViaje());
        helper.setText(htmlBody, true);

        // --- ¡AMBAS LÍNEAS SON NECESARIAS! ---
        // 1. Adjunta la imagen del bus para usarla con cid:busImage
        helper.addInline("busImage", new ClassPathResource("static/bus.png"));

        // 2. Adjunta la imagen del QR generada para usarla con cid:qrCodeImage
        helper.addInline("qrCodeImage", new ByteArrayResource(qrCode), "image/png");

        mailSender.send(mimeMessage);
        logger.info("Email con el ticket (ID: {}) y QR construido y enviado exitosamente a {}", pasaje.getId(), pasaje.getClienteEmail());
    }

    // Este método privado se mantiene exactamente igual
    private String buildTicketHtml(PasajeResponseDTO pasaje) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { font-family: Arial, Helvetica, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
                .ticket-container { max-width: 800px; margin: auto; border-radius: 12px; display: flex; box-shadow: 0 5px 15px rgba(0,0,0,0.1); background-color: #ffffff; overflow: hidden; border: 1px solid #ddd; }
                
                .main-part { flex-grow: 1; padding: 25px; }
                .header-title { font-size: 26px; font-weight: bold; color: #333; }
                .ticket-id { font-size: 14px; color: #777; font-family: monospace; }
                
                .content-wrapper { display: flex; justify-content: space-between; align-items: flex-start; margin-top: 25px; gap: 20px; }
                
                /* Usamos una tabla para máxima compatibilidad en clientes de email */
                .info-table { width: 100%%; border-collapse: collapse; }
                .info-table td { padding: 6px 0; vertical-align: top; font-size: 15px; }
                .info-table td.label { width: 90px; font-weight: 600; color: #555; }
                .info-table td.value { color: #333; }
                
                .route-box { background: #fff8e1; border-radius: 10px; padding: 20px; text-align: center; color: #c89200; font-weight: bold; min-width: 200px;}
                .route-box .city { font-size: 20px; font-weight: 700; }
                .route-box .arrow { font-size: 24px; margin: 5px 0; color: #ffc107; }

                .stub-part {
                    width: 240px;
                    background: linear-gradient(to bottom, #ffc107, #ffa000);
                    color: white;
                    padding: 25px;
                    border-left: 2px dashed #ffffff;
                    text-align: center;
                    display: flex;
                    flex-direction: column;
                    justify-content: center;
                    align-items: center;
                }
                .stub-header { font-size: 22px; font-weight: bold; text-transform: uppercase; margin-bottom: 20px; }
                .stub-part img.bus-image { width: 140px; margin-bottom: 25px; }
                .stub-part img.qr-code { width: 150px; height: 150px; border: 4px solid white; border-radius: 6px; }
                .stub-part .ticket-id-stub { font-size: 14px; margin-top: 15px; font-family: monospace; color: white; }

            </style>
        </head>
        <body>
            <div class="ticket-container">
                <div class="main-part">
                    <div>
                        <span class="header-title">BUS TICKET</span>
                        <span style="float: right;" class="ticket-id">#%s</span>
                    </div>
                    <div class="content-wrapper">
                        <!-- Tabla para la información del ticket -->
                        <table class="info-table" border="0" cellpadding="0" cellspacing="0">
                            <tr><td class="label">Pasajero:</td><td class="value">%s</td></tr>
                            <tr><td class="label">Fecha:</td><td class="value">%s</td></tr>
                            <tr><td class="label">Hora:</td><td class="value">%s</td></tr>
                            <tr><td class="label">Bus:</td><td class="value">%s</td></tr>
                            <tr><td class="label">Asiento:</td><td class="value">%d</td></tr>
                            <tr><td class="label">Precio:</td><td class="value">€ %.2f</td></tr>
                        </table>
                        <div class="route-box">
                            <div class="city">%s</div>
                            <div class="arrow">↓</div>
                            <div class="city">%s</div>
                        </div>
                    </div>
                </div>
                <div class="stub-part">
                    <div class="stub-header">Boarding Pass</div>
                    <img src="cid:busImage" alt="Bus" class="bus-image">
                    <img src="cid:qrCodeImage" alt="QR Code" class="qr-code">
                    <div class="ticket-id-stub">#%s</div>
                </div>
            </div>
        </body>
        </html>
    """.formatted(
                String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000), // ID para el header
                pasaje.getClienteNombre(),
                pasaje.getFechaViaje().format(dateFormatter),
                pasaje.getHoraSalidaViaje().format(timeFormatter),
                pasaje.getOmnibusMatricula(),
                pasaje.getNumeroAsiento(),
                pasaje.getPrecio(),
                pasaje.getOrigenViaje().toUpperCase(),
                pasaje.getDestinoViaje().toUpperCase(),
                String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000) // ID para el talón
        );
    }
}