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

import java.util.Base64;
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
        // El 'true' para multipart y 'UTF-8' para la codificación son muy importantes.
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        // 1. Generar los bytes del código QR
        String qrText = "Ticket ID: " + pasaje.getId() + " | Pasajero: " + pasaje.getClienteNombre() + " | Viaje: " + pasaje.getViajeId();
        byte[] qrCodeBytes = qrCodeService.generateQrCodeImage(qrText, 250, 250);

        // 2. Convertir los bytes del QR a una cadena Base64 para incrustarlo en el HTML.
        // Esto garantiza que se vea en el PDF y en la mayoría de los clientes de correo.
        String qrCodeBase64 = Base64.getEncoder().encodeToString(qrCodeBytes);

        // 3. Construir el cuerpo HTML del correo, pasando la cadena Base64 del QR.
        String htmlBody = buildTicketHtml(pasaje, qrCodeBase64);

        // 4. Generar el archivo PDF a partir del mismo cuerpo HTML.
        // Como el HTML ahora contiene el QR en Base64, el PDF también lo tendrá.
        byte[] pdfAttachment = createTicketPdf(htmlBody);

        // 5. Configurar los detalles del correo electrónico.
        helper.setTo(pasaje.getClienteEmail());
        helper.setFrom(fromEmail);
        helper.setSubject("Tu pasaje de bus para el viaje a " + pasaje.getDestinoViaje());
        helper.setText(htmlBody, true); // Se establece el HTML como cuerpo del correo.

        // 6. Adjuntar el recurso de imagen del QR con un Content-ID (cid).
        // Esto sirve como respaldo para clientes de correo muy antiguos que no soportan Base64 en <img>.
        // Aunque la mayoría ya no lo necesite, es una buena práctica de "mejora progresiva".
        helper.addInline("qrCodeImageCid", new ByteArrayResource(qrCodeBytes), "image/png");

        // 7. Adjuntar el archivo PDF generado.
        String pdfFileName = "Pasaje-" + pasaje.getId() + ".pdf";
        helper.addAttachment(pdfFileName, new ByteArrayResource(pdfAttachment), "application/pdf");

        // 8. Enviar el correo.
        mailSender.send(mimeMessage);
        logger.info("Email con el ticket (HTML y PDF adjunto) enviado exitosamente a {}", pasaje.getClienteEmail());
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
    private String buildTicketHtml(PasajeResponseDTO pasaje, String qrCodeBase64) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        String ticketNumber = String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000);
        String formattedPrice = String.format("€ %.2f", pasaje.getPrecio());

        // El Data URI para la imagen QR
        String qrCodeSrc = "data:image/png;base64," + qrCodeBase64;

        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
            <title>Tu Pasaje de Bus</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 0; background-color: #e9ecef; }
                .ticket-container { max-width: 800px; margin: 20px auto; box-shadow: 0 10px 25px rgba(0,0,0,0.1); border-radius: 12px; }
                /* Parte izquierda con fondo gris claro */
                .main-part { padding: 35px; background-color: #f8f9fa; border-top-left-radius: 12px; border-bottom-left-radius: 12px; }
                /* Parte derecha con fondo blanco para contraste */
                .stub-part { width: 280px; background-color: #ffffff; border-top-right-radius: 12px; border-bottom-right-radius: 12px; }
                .header h1 { font-size: 24px; font-weight: 600; color: #1a202c; margin: 0; }
                .header span { font-size: 14px; color: #718096; }
                .info-table td { padding: 6px 0; vertical-align: top; }
                .info-table strong { font-weight: 600; color: #4a5568; padding-right: 10px; }
                .info-table span { color: #2d3748; }
                /* Caja de ruta con fondo blanco para que resalte en el fondo gris */
                .route-box { background-color: #ffffff; border: 1px solid #dee2e6; border-radius: 8px; padding: 20px; text-align: center; color: #2d3748; }
                .route-box .city { font-size: 20px; font-weight: 600; text-transform: uppercase;}
                .route-box .arrow { font-size: 24px; color: #a0aec0; margin: 8px 0; line-height: 1; }
                .stub-header { font-size: 20px; font-weight: 600; color: #1a202c; margin-bottom: 25px; text-align: center;}
                .qr-code { width: 180px; height: 180px; margin-bottom: 20px; }
                .ticket-number-stub { font-size: 16px; font-weight: 600; color: #718096; letter-spacing: 1px; text-align: center; }
            </style>
        </head>
        <body style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background-color: #e9ecef;">
            <table class="ticket-container" width="800" align="center" cellpadding="0" cellspacing="0" role="presentation" style="width:800px; max-width:800px; margin:20px auto; border-radius:12px; box-shadow: 0 10px 25px rgba(0,0,0,0.1);">
                <tr>
                    <td class="main-part" style="padding:35px; background-color:#f8f9fa; border-top-left-radius: 12px; border-bottom-left-radius: 12px; border-right: 2px dashed #d8dde3;">
                        <div class="header" style="padding-bottom: 20px; border-bottom: 1px solid #dee2e6; margin-bottom: 25px;">
                            <h1 style="font-size:24px; font-weight:600; color:#1a202c; margin:0;">Bus Ticket</h1>
                            <span style="font-size:14px; color:#718096;">Ticket ID: #%s</span>
                        </div>
                        <table width="100%%" cellpadding="0" cellspacing="0" role="presentation">
                            <tr>
                                <td width="55%%" style="vertical-align: top;">
                                    <table class="info-table" width="100%%" cellpadding="0" cellspacing="0" role="presentation">
                                        <tr><td style="padding: 6px 0; vertical-align: top;"><strong style="font-weight:600; color:#4a5568; padding-right:10px;">Pasajero</strong><span style="color:#2d3748;">: %s</span></td></tr>
                                        <tr><td style="padding: 6px 0; vertical-align: top;"><strong style="font-weight:600; color:#4a5568; padding-right:10px;">Fecha</strong><span style="color:#2d3748;">: %s</span></td></tr>
                                        <tr><td style="padding: 6px 0; vertical-align: top;"><strong style="font-weight:600; color:#4a5568; padding-right:10px;">Hora</strong><span style="color:#2d3748;">: %s</span></td></tr>
                                        <tr><td style="padding: 6px 0; vertical-align: top;"><strong style="font-weight:600; color:#4a5568; padding-right:10px;">Omnibus</strong><span style="color:#2d3748;">: %s</span></td></tr>
                                        <tr><td style="padding: 6px 0; vertical-align: top;"><strong style="font-weight:600; color:#4a5568; padding-right:10px;">Asiento</strong><span style="color:#2d3748;">: %d</span></td></tr>
                                        <tr><td style="padding: 6px 0; vertical-align: top;"><strong style="font-weight:600; color:#4a5568; padding-right:10px;">Clase</strong><span style="color:#2d3748;">: B</span></td></tr>
                                        <tr><td style="padding: 6px 0; vertical-align: top;"><strong style="font-weight:600; color:#4a5568; padding-right:10px;">Precio</strong><span style="color:#2d3748;">: %s</span></td></tr>
                                    </table>
                                </td>
                                <td width="45%%" style="padding-left: 20px; vertical-align: middle;">
                                    <div class="route-box" style="background-color:#ffffff; border: 1px solid #dee2e6; border-radius:8px; padding:20px; text-align:center; color:#2d3748;">
                                        <div class="city" style="font-size:20px; font-weight:600; text-transform:uppercase;">%s</div>
                                        <div class="arrow" style="font-size:24px; color:#a0aec0; margin:8px 0; line-height:1;">↓</div>
                                        <div class="city" style="font-size:20px; font-weight:600; text-transform:uppercase;">%s</div>
                                    </div>
                                </td>
                            </tr>
                        </table>
                    </td>
                    <td class="stub-part" width="280" style="width:280px; background-color:#ffffff; padding:35px; text-align:center; vertical-align:middle; border-top-right-radius: 12px; border-bottom-right-radius: 12px;">
                         <div class="stub-header" style="font-size:20px; font-weight:600; color:#1a202c; margin-bottom:25px;">ABORDAR AQUÍ</div>
                         <img src="%s" alt="QR Code" class="qr-code" width="180" height="180" style="width:180px; height:180px; margin-bottom:20px;" />
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
                qrCodeSrc, // <-- El QR en Base64 se inserta aquí
                ticketNumber
        );
    }
}