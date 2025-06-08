package com.omnibus.backend.service;

import com.google.zxing.WriterException;
import com.omnibus.backend.dto.PasajeResponseDTO;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder; // <-- NUEVA IMPORTACIÓN
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream; // <-- NUEVA IMPORTACIÓN
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
     * MODIFICADO: Ahora también genera un PDF del ticket y lo adjunta al correo.
     */
    public void buildAndSendTicket(PasajeResponseDTO pasaje) throws MessagingException, WriterException, IOException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        // 1. Generar el QR
        String qrText = "Ticket ID: " + pasaje.getId() + " | Pasajero: " + pasaje.getClienteNombre() + " | Viaje: " + pasaje.getViajeId();
        byte[] qrCode = qrCodeService.generateQrCodeImage(qrText, 250, 250);

        // 2. Construir el cuerpo HTML del correo
        String htmlBody = buildTicketHtml(pasaje);

        // 3. (NUEVO) Generar el PDF a partir del mismo HTML
        byte[] pdfAttachment = createTicketPdf(htmlBody);

        // 4. Configurar el correo
        helper.setTo(pasaje.getClienteEmail());
        helper.setFrom(fromEmail);
        helper.setSubject("Tu pasaje de bus para el viaje a " + pasaje.getDestinoViaje());
        helper.setText(htmlBody, true); // El HTML se muestra en el cuerpo del correo

        // 5. Adjuntar los recursos (QR para el HTML y el PDF)
        helper.addInline("qrCodeImage", new ByteArrayResource(qrCode), "image/png");
        helper.addAttachment("Pasaje-" + pasaje.getId() + ".pdf", new ByteArrayResource(pdfAttachment), "application/pdf");

        // 6. Enviar el correo
        mailSender.send(mimeMessage);
        logger.info("Email con el ticket (HTML y PDF) enviado exitosamente a {}", pasaje.getClienteEmail());
    }

    /**
     * NUEVO: Este método privado convierte una cadena HTML en un PDF usando OpenHTMLtoPDF.
     * @param htmlContent El HTML del ticket.
     * @return un array de bytes con el contenido del PDF.
     * @throws IOException Si ocurre un error de I/O.
     */
    private byte[] createTicketPdf(String htmlContent) throws IOException {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }


    /**
     * MODIFICADO: HTML y CSS rediseñados para un aspecto más limpio y moderno.
     * Se eliminó la imagen del bus.
     */
    private String buildTicketHtml(PasajeResponseDTO pasaje) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        String ticketNumber = String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000);
        String formattedPrice = String.format("€ %.2f", pasaje.getPrecio());

        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8" /> <!-- CAMBIO AQUÍ -->
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background-color: #f0f2f5; }
                    .ticket-container { max-width: 800px; margin: auto; display: flex; box-shadow: 0 10px 25px rgba(0,0,0,0.1); border-radius: 12px; background-color: #ffffff; }
                    .main-part { flex-grow: 1; padding: 30px; border-right: 2px dashed #e0e0e0; }
                    .stub-part { width: 280px; min-width: 280px; background-color: #f7f9fc; padding: 30px; text-align: center; display: flex; flex-direction: column; justify-content: center; align-items: center; border-top-right-radius: 12px; border-bottom-right-radius: 12px;}
                    .header { padding-bottom: 20px; border-bottom: 1px solid #eeeeee; margin-bottom: 25px; }
                    .header h1 { font-size: 24px; font-weight: 600; color: #1a202c; margin: 0; }
                    .header span { font-size: 14px; color: #718096; }
                    .ticket-info { display: grid; grid-template-columns: 100px 1fr; gap: 12px 15px; }
                    .ticket-info strong { font-weight: 600; color: #4a5568; }
                    .ticket-info span { color: #2d3748; }
                    .main-content { display: flex; justify-content: space-between; align-items: flex-start; }
                    .route-box { background-color: #edf2f7; border-radius: 8px; padding: 20px; text-align: center; color: #2d3748; }
                    .route-box .city { font-size: 22px; font-weight: 600; }
                    .route-box .arrow { font-size: 24px; color: #a0aec0; margin: 8px 0; }
                    .stub-header { font-size: 22px; font-weight: 600; color: #1a202c; margin-bottom: 25px; }
                    .qr-code { width: 180px; height: 180px; margin-bottom: 20px; }
                    .ticket-number-stub { font-size: 16px; font-weight: 600; color: #718096; letter-spacing: 1px; }
                </style>
            </head>
            <body>
                <div class="ticket-container">
                    <div class="main-part">
                        <div class="header">
                            <h1>Bus Ticket</h1>
                            <span>Ticket ID: #%s</span>
                        </div>
                        <div class="main-content">
                            <div class="ticket-info">
                                <strong>Pasajero</strong><span>: %s</span>
                                <strong>Fecha</strong><span>: %s</span>
                                <strong>Hora</strong><span>: %s</span>
                                <strong>Omnibus</strong><span>: %s</span>
                                <strong>Asiento</strong><span>: %d</span>
                                <strong>Clase</strong><span>: B</span>
                                <strong>Precio</strong><span>: %s</span>
                            </div>
                            <div class="route-box">
                                <div class="city">%s</div>
                                <div class="arrow">↓</div>
                                <div class="city">%s</div>
                            </div>
                        </div>
                    </div>
                    <div class="stub-part">
                        <div class="stub-header">ABORDAR AQUÍ</div>
                        <img src="cid:qrCodeImage" alt="QR Code" class="qr-code" /> <!-- CAMBIO AQUÍ -->
                        <div class="ticket-number-stub">#%s</div>
                    </div>
                </div>
            </body>
            </html>
        """.formatted(
                ticketNumber,
                pasaje.getClienteNombre(),
                pasaje.getFechaViaje().format(dateFormatter),
                pasaje.getHoraSalidaViaje().format(timeFormatter),
                pasaje.getOmnibusMatricula(),
                pasaje.getNumeroAsiento(),
                formattedPrice,
                pasaje.getOrigenViaje().toUpperCase(),
                pasaje.getDestinoViaje().toUpperCase(),
                ticketNumber
        );
    }
}