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

        helper.addInline("busImage", new ClassPathResource("static/bus.png"));
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
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background-color: #f4f4f4; }
                .ticket-container { max-width: 800px; margin: auto; border-radius: 15px; display: flex; box-shadow: 0 6px 12px rgba(0,0,0,0.1); background-color: #fff; overflow: hidden; }
                
                /* Parte Principal (Izquierda) */
                .main-part { flex-grow: 1; padding: 30px; background: #fff; display: flex; flex-direction: column; }
                .header { padding-bottom: 20px; border-bottom: 1px solid #eee; margin-bottom: 20px;}
                .header-title { font-size: 28px; font-weight: bold; color: #333; letter-spacing: 1px; }
                .ticket-id { font-size: 16px; color: #888; font-family: monospace;}
                
                .content-wrapper { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; }
                
                .ticket-info { display: grid; grid-template-columns: 90px 1fr; gap: 10px 15px; font-size: 15px; }
                .ticket-info strong { color: #555; font-weight: 600; }
                .ticket-info span { color: #333; }
                
                .route-box { background: #fff8e1; border: 1px solid #ffecc1; border-radius: 10px; padding: 20px; text-align: center; color: #d29c00; font-weight: bold; align-self: center; }
                .route-box .city { font-size: 22px; font-weight: 700; }
                .route-box .arrow { font-size: 28px; margin: 8px 0; color: #ffc107; }

                /* Talón (Derecha) */
                .stub-part {
                    width: 250px;
                    background: linear-gradient(135deg, #ffc107, #ffa000);
                    color: white;
                    padding: 30px;
                    border-left: 3px dashed #fff;
                    text-align: center;
                    display: flex;
                    flex-direction: column;
                    justify-content: space-between;
                    align-items: center;
                }
                .stub-header { font-size: 24px; font-weight: bold; letter-spacing: 1px; text-transform: uppercase; }
                .stub-part img.bus-image { width: 150px; margin: 15px 0; filter: drop-shadow(0 4px 6px rgba(0,0,0,0.2)); }
                .stub-part img.qr-code { width: 160px; height: 160px; margin-top: 15px; border: 5px solid white; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.1); }

            </style>
        </head>
        <body>
            <div class="ticket-container">
                <div class="main-part">
                    <div class="header">
                        <span class="header-title">BUS TICKET</span>
                        <span class="ticket-id" style="float: right;">#%s</span>
                    </div>
                    <div class="content-wrapper">
                        <div class="ticket-info">
                            <strong>Pasajero</strong><span>: %s</span>
                            <strong>Fecha</strong><span>: %s</span>
                            <strong>Hora</strong><span>: %s</span>
                            <strong>Bus</strong><span>: %s</span>
                            <strong>Asiento</strong><span>: %d</span>
                            <strong>Precio</strong><span>: € %.2f</span>
                        </div>
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
                    <div class="ticket-id">#%s</div>
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