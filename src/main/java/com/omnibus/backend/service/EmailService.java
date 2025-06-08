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
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        String qrText = "Ticket ID: " + pasaje.getId() + " | Pasajero: " + pasaje.getClienteNombre() + " | Viaje: " + pasaje.getViajeId();
        byte[] qrCode = qrCodeService.generateQrCodeImage(qrText, 250, 250);

        String htmlBody = buildTicketHtml(pasaje);

        helper.setTo(pasaje.getClienteEmail());
        helper.setFrom(fromEmail);
        helper.setSubject("Tu pasaje de bus para el viaje a " + pasaje.getDestinoViaje());
        helper.setText(htmlBody, true);

        helper.addInline("busImage", new ClassPathResource("static/images/bus.png"));
        helper.addInline("qrCodeImage", new ByteArrayResource(qrCode), "image/png");

        mailSender.send(mimeMessage);
        logger.info("Email con el ticket y QR construido y enviado exitosamente a {}", pasaje.getClienteEmail());
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
                <style>
                    body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
                    .ticket-container { max-width: 800px; margin: auto; border: 1px solid #ddd; border-radius: 15px; display: flex; box-shadow: 0 4px 8px rgba(0,0,0,0.1); background-color: #fff; }
                    .main-part { flex-grow: 1; padding: 25px; background: #fff8e1; border-top-left-radius: 15px; border-bottom-left-radius: 15px; }
                    .stub-part { width: 250px; background: linear-gradient(to bottom, #ffc107, #ff9800); color: #333; padding: 25px; border-top-right-radius: 15px; border-bottom-right-radius: 15px; border-left: 2px dashed #fff; text-align: center; }
                    .header { font-size: 28px; font-weight: bold; color: #4CAF50; letter-spacing: 2px; padding: 10px; background: linear-gradient(to right, #ffc107, #ff9800); border-top-left-radius: 15px; margin: -25px -25px 20px -25px; text-align: left; padding-left: 25px;}
                    .ticket-info { display: grid; grid-template-columns: 100px 1fr; gap: 8px 15px; margin-bottom: 20px; }
                    .ticket-info strong { color: #555; }
                    .route-box { background: #ffc107; border-radius: 10px; padding: 15px; text-align: center; margin-right: 20px; color: #fff; font-weight: bold;}
                    .main-content { display: flex; align-items: center; }
                    .stub-header { font-size: 24px; font-weight: bold; color: #fff; letter-spacing: 1px;}
                </style>
            </head>
            <body>
                <div class="ticket-container">
                    <div class="main-part">
                        <div class="header">BUS TICKET <span style="float:right; font-size: 16px; color: #555; margin-top: 8px;">#%s</span></div>
                        <div class="main-content">
                            <div>
                                <div class="ticket-info">
                                    <strong>Date</strong><span>: %s</span>
                                    <strong>Time</strong><span>: %s</span>
                                    <strong>Name</strong><span>: %s</span>
                                    <strong>Bus</strong><span>: %s</span>
                                    <strong>Seat</strong><span>: %d</span>
                                    <strong>Class</strong><span>: B</span>
                                    <strong>Price</strong><span>: € %.2f</span>
                                </div>
                            </div>
                            <div style="margin-left: auto; text-align: center;">
                                <div class="route-box">
                                    <div style="font-size: 20px;">%s</div>
                                    <div style="font-size: 24px; margin: 5px 0;">↓</div>
                                    <div style="font-size: 20px;">%s</div>
                                </div>
                            </div>
                        </div>
                    </div>
                    <div class="stub-part">
                        <div class="stub-header">BUS TICKET</div>
                        <img src="cid:busImage" alt="Bus" style="width: 180px; margin: 20px 0;">
                        <img src="cid:qrCodeImage" alt="QR Code" style="width: 150px; height: 150px; margin-top: 10px;">
                        <div style="font-size: 14px; margin-top: 10px; color: #fff; font-weight:bold;">#%s</div>
                    </div>
                </div>
            </body>
            </html>
        """.formatted(
                String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000),
                pasaje.getFechaViaje().format(dateFormatter),
                pasaje.getHoraSalidaViaje().format(timeFormatter),
                pasaje.getClienteNombre(),
                pasaje.getOmnibusMatricula(),
                pasaje.getNumeroAsiento(),
                pasaje.getPrecio(),
                pasaje.getOrigenViaje().toUpperCase(),
                pasaje.getDestinoViaje().toUpperCase(),
                String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000)
        );
    }
}