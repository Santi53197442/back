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

        // Se ha rediseñado todo con tablas para máxima compatibilidad.
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
                <title>Tu Pasaje de Bus</title>
                <style>
                    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 0; background-color: #f0f2f5; }
                    .ticket-container { max-width: 800px; margin: 20px auto; box-shadow: 0 10px 25px rgba(0,0,0,0.1); border-radius: 12px; background-color: #ffffff; }
                    .main-part { padding: 35px; }
                    .stub-part { width: 280px; background-color: #f8f9fa; border-top-right-radius: 12px; border-bottom-right-radius: 12px; }
                    .header h1 { font-size: 24px; font-weight: 600; color: #1a202c; margin: 0; }
                    .header span { font-size: 14px; color: #718096; }
                    .info-table td { padding: 6px 0; vertical-align: top; }
                    .info-table strong { font-weight: 600; color: #4a5568; padding-right: 10px; }
                    .info-table span { color: #2d3748; }
                    .route-box { background-color: #f1f5f9; border-radius: 8px; padding: 20px; text-align: center; color: #2d3748; }
                    .route-box .city { font-size: 20px; font-weight: 600; text-transform: uppercase;}
                    .route-box .arrow { font-size: 24px; color: #a0aec0; margin: 8px 0; line-height: 1; }
                    .stub-header { font-size: 20px; font-weight: 600; color: #1a202c; margin-bottom: 25px; text-align: center;}
                    .qr-code { width: 180px; height: 180px; margin-bottom: 20px; }
                    .ticket-number-stub { font-size: 16px; font-weight: 600; color: #718096; letter-spacing: 1px; text-align: center; }
                </style>
            </head>
            <body>
                <table class="ticket-container" width="800" align="center" cellpadding="0" cellspacing="0" role="presentation" style="width:800px; max-width:800px; margin:20px auto; background-color:#ffffff; border-radius:12px; box-shadow: 0 10px 25px rgba(0,0,0,0.1);">
                    <tr>
                        <td class="main-part" style="padding:35px; border-right: 2px dashed #e0e0e0;">
                            <!-- Header -->
                            <div class="header" style="padding-bottom: 20px; border-bottom: 1px solid #eeeeee; margin-bottom: 25px;">
                                <h1 style="font-size:24px; font-weight:600; color:#1a202c; margin:0;">Bus Ticket</h1>
                                <span style="font-size:14px; color:#718096;">Ticket ID: #%s</span>
                            </div>

                            <!-- Content Table (Info + Route) -->
                            <table width="100%%" cellpadding="0" cellspacing="0" role="presentation">
                                <tr>
                                    <td width="55%%" style="vertical-align: top;">
                                        <table class="info-table" width="100%%" cellpadding="0" cellspacing="0" role="presentation">
                                            <tr><td><strong>Pasajero</strong><span>: %s</span></td></tr>
                                            <tr><td><strong>Fecha</strong><span>: %s</span></td></tr>
                                            <tr><td><strong>Hora</strong><span>: %s</span></td></tr>
                                            <tr><td><strong>Omnibus</strong><span>: %s</span></td></tr>
                                            <tr><td><strong>Asiento</strong><span>: %d</span></td></tr>
                                            <tr><td><strong>Clase</strong><span>: B</span></td></tr>
                                            <tr><td><strong>Precio</strong><span>: %s</span></td></tr>
                                        </table>
                                    </td>
                                    <td width="45%%" style="padding-left: 20px; vertical-align: top;">
                                        <div class="route-box" style="background-color:#f1f5f9; border-radius:8px; padding:20px; text-align:center; color:#2d3748;">
                                            <div class="city" style="font-size:20px; font-weight:600; text-transform:uppercase;">%s</div>
                                            <div class="arrow" style="font-size:24px; color:#a0aec0; margin:8px 0; line-height:1;">↓</div>
                                            <div class="city" style="font-size:20px; font-weight:600; text-transform:uppercase;">%s</div>
                                        </div>
                                    </td>
                                </tr>
                            </table>
                        </td>
                        <td class="stub-part" width="280" style="width:280px; background-color:#f8f9fa; padding:35px; text-align:center; vertical-align:middle; border-top-right-radius: 12px; border-bottom-right-radius: 12px;">
                             <div class="stub-header" style="font-size:20px; font-weight:600; color:#1a202c; margin-bottom:25px;">ABORDAR AQUÍ</div>
                             <img src="cid:qrCodeImage" alt="QR Code" class="qr-code" width="180" height="180" style="width:180px; height:180px; margin-bottom:20px;" />
                             <div class="ticket-number-stub" style="font-size:16px; font-weight:600; color:#718096; letter-spacing:1px;">#%s</div>
                        </td>
                    </tr>
                </table>
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
                pasaje.getOrigenViaje(),
                pasaje.getDestinoViaje(),
                ticketNumber
        );
    }
}