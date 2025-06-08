package com.omnibus.backend.service;

import com.google.zxing.WriterException;
import com.omnibus.backend.dto.PasajeResponseDTO;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

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

    public void sendPasswordResetEmail(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject("Restablecimiento de Contraseña");
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        message.setText("Hola,\n\nHas solicitado restablecer tu contraseña.\n" +
                "Haz clic en el siguiente enlace para continuar:\n" + resetUrl);
        try {
            mailSender.send(message);
            logger.info("Correo de restablecimiento enviado a: {}", to);
        } catch (Exception e) {
            logger.error("Error al enviar correo de restablecimiento a {}: {}", to, e.getMessage());
        }
    }

    /**
     * Construye el correo, genera un PDF del ticket y lo adjunta.
     */
    public void buildAndSendTicket(PasajeResponseDTO pasaje) throws MessagingException, WriterException, IOException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        // 1. Generar el código QR
        byte[] qrCodeBytes = qrCodeService.generateQrCodeImage(
                "Ticket ID: " + pasaje.getId() + " | Pasajero: " + pasaje.getClienteNombre(), 250, 250);

        // 2. Construir el cuerpo del correo en HTML
        String htmlTemplate = buildTicketHtml();
        String htmlBodyForEmail = htmlTemplate.formatted(getTicketData(pasaje, "cid:qrCodeImage"));
        helper.setText(htmlBodyForEmail, true);

        // 3. Generar el PDF del ticket
        byte[] pdfBytes = generateTicketPdf(pasaje, qrCodeBytes);

        // 4. Configurar el correo y adjuntar todo
        helper.setTo(pasaje.getClienteEmail());
        helper.setFrom(fromEmail);
        helper.setSubject("✅ Tu pasaje de bus para el viaje a " + pasaje.getDestinoViaje());

        // Adjuntar el QR para que se vea en el cuerpo del email (usando cid)
        helper.addInline("qrCodeImage", new ByteArrayResource(qrCodeBytes), "image/png");

        // Adjuntar el PDF generado
        String pdfFileName = "Pasaje-" + pasaje.getOrigenViaje() + "-" + pasaje.getId() + ".pdf";
        helper.addAttachment(pdfFileName, new ByteArrayResource(pdfBytes), "application/pdf");

        mailSender.send(mimeMessage);
        logger.info("Email con ticket (ID: {}) y PDF adjunto enviado a {}", pasaje.getId(), pasaje.getClienteEmail());
    }

    /**
     * Nuevo método para generar el PDF a partir del HTML.
     */
    private byte[] generateTicketPdf(PasajeResponseDTO pasaje, byte[] qrCodeBytes) throws IOException {
        // Convertimos el QR a Base64 para incrustarlo directamente en el HTML del PDF
        String qrCodeBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrCodeBytes);

        // Obtenemos la plantilla HTML y la formateamos con los datos y el QR en Base64
        String htmlTemplate = buildTicketHtml();
        String finalHtml = htmlTemplate.formatted(getTicketData(pasaje, qrCodeBase64));

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(finalHtml, null);
            builder.toStream(os);
            builder.run();
            logger.info("PDF para pasaje ID {} generado correctamente.", pasaje.getId());
            return os.toByteArray();
        } catch (Exception e) {
            logger.error("Error al generar el PDF para el pasaje ID {}: {}", pasaje.getId(), e.getMessage(), e);
            throw new IOException("No se pudo generar el PDF del ticket.", e);
        }
    }

    /**
     * Método auxiliar para obtener los datos del ticket en el orden correcto para el formateo.
     */
    private Object[] getTicketData(PasajeResponseDTO pasaje, String qrImageSource) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        return new Object[]{
                String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000), // 1. ID header
                pasaje.getClienteNombre(),                                              // 2. Nombre
                pasaje.getFechaViaje().format(dateFormatter),                           // 3. Fecha
                pasaje.getHoraSalidaViaje().format(timeFormatter),                      // 4. Hora
                pasaje.getOmnibusMatricula(),                                           // 5. Matrícula
                pasaje.getNumeroAsiento(),                                              // 6. Asiento
                pasaje.getPrecio(),                                                     // 7. Precio
                pasaje.getOrigenViaje().toUpperCase(),                                  // 8. Origen
                pasaje.getDestinoViaje().toUpperCase(),                                 // 9. Destino
                qrImageSource,                                                          // 10. Fuente de la imagen QR
                String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000)  // 11. ID talón
        };
    }

    /**
     * Plantilla HTML única. Devuelve el string sin formatear.
     */
    private String buildTicketHtml() {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; margin: 0; padding: 20px; background-color: #f9f9f9; }
                    .ticket-container { max-width: 800px; margin: auto; border-radius: 12px; display: flex; box-shadow: 0 5px 15px rgba(0,0,0,0.1); background-color: #ffffff; border: 1px solid #e0e0e0; }
                    .main-part { width: 65%; padding: 30px; }
                    .stub-part { width: 35%; background: linear-gradient(to bottom, #ffc107, #ffa000); text-align: center; padding: 30px; border-top-right-radius: 11px; border-bottom-right-radius: 11px; display: flex; flex-direction: column; justify-content: center; align-items: center; }
                    .header-title { font-size: 28px; font-weight: bold; color: #333; }
                    .ticket-id { font-size: 14px; color: #888; font-family: monospace; }
                    .content-wrapper { display: flex; margin-top: 25px; gap: 20px; }
                    .info-table { width: 60%; border-collapse: collapse; }
                    .info-table td { padding: 8px 0; vertical-align: top; font-size: 15px; }
                    .info-table td.label { width: 90px; font-weight: 600; color: #555; }
                    .route-box { flex-grow: 1; background: #fff8e1; border-radius: 10px; padding: 15px; text-align: center; color: #c89200; }
                    .route-box .city { font-size: 18px; font-weight: 700; }
                    .route-box .arrow { font-size: 22px; margin: 5px 0; color: #ffc107; }
                    .stub-header { font-size: 24px; font-weight: bold; text-transform: uppercase; color: white; margin-bottom: 30px; }
                    .qr-code { width: 180px; height: 180px; border: 5px solid white; border-radius: 8px; box-shadow: 0 4px 10px rgba(0,0,0,0.2); }
                    .ticket-id-stub { font-size: 14px; margin-top: 20px; font-family: monospace; color: white; }
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
                            <table class="info-table" border="0" cellpadding="0" cellspacing="0">
                                <tr><td class="label">Pasajero:</td><td>%s</td></tr>
                                <tr><td class="label">Fecha:</td><td>%s</td></tr>
                                <tr><td class="label">Hora:</td><td>%s</td></tr>
                                <tr><td class="label">Bus:</td><td>%s</td></tr>
                                <tr><td class="label">Asiento:</td><td>%d</td></tr>
                                <tr><td class="label">Precio:</td><td>€ %.2f</td></tr>
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
                        <!-- Este %s será reemplazado por 'cid:qrCodeImage' para el email o por la data base64 para el PDF -->
                        <img src="%s" alt="QR Code" class="qr-code">
                        <div class="ticket-id-stub">#%s</div>
                    </div>
                </div>
            </body>
            </html>
        """;
    }
}