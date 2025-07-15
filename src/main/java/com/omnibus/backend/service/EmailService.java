package com.omnibus.backend.service;

import com.google.zxing.WriterException;
import com.omnibus.backend.dto.PasajeResponseDTO;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.model.Viaje;
import com.omnibus.backend.repository.PasajeRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder; // <-- NUEVA IMPORTACIÓN
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityNotFoundException;
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
import java.util.Locale;

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

    @Autowired
    private PasajeRepository pasajeRepository;

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

        // 1. Generar los bytes del código QR una sola vez
        String qrText = "Ticket ID: " + pasaje.getId() + " | Pasajero: " + pasaje.getClienteNombre() + " | Viaje: " + pasaje.getViajeId();
        byte[] qrCodeBytes = qrCodeService.generateQrCodeImage(qrText, 250, 250);

        // 2. Preparar los dos tipos de 'src' para la imagen del QR
        //    - Para el PDF: se usa Base64.
        String qrSrcForPdf = "data:image/png;base64," + Base64.getEncoder().encodeToString(qrCodeBytes);
        //    - Para el email: se usa Content-ID (cid).
        String qrSrcForEmail = "cid:qrCodeImage";

        // 3. Generar el HTML para el PDF y crear el adjunto
        String htmlForPdf = buildTicketHtml(pasaje, qrSrcForPdf);
        byte[] pdfAttachment = createTicketPdf(htmlForPdf);

        // 4. Generar el HTML para el cuerpo del correo
        String htmlBodyForEmail = buildTicketHtml(pasaje, qrSrcForEmail);

        // 5. Configurar los detalles del correo electrónico
        helper.setTo(pasaje.getClienteEmail());
        helper.setFrom(fromEmail);
        helper.setSubject("Tu pasaje de bus para el viaje a " + pasaje.getDestinoViaje());
        helper.setText(htmlBodyForEmail, true); // Usamos el HTML específico para email

        // 6. Adjuntar el recurso de imagen inline para que el 'cid:qrCodeImage' funcione
        helper.addInline("qrCodeImage", new ByteArrayResource(qrCodeBytes), "image/png");

        // 7. Adjuntar el archivo PDF
        String pdfFileName = "Pasaje-" + pasaje.getId() + ".pdf";
        helper.addAttachment(pdfFileName, new ByteArrayResource(pdfAttachment));

        // 8. Enviar
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
    private String buildTicketHtml(PasajeResponseDTO pasaje, String qrCodeSrc) { // <-- CAMBIO EN LA FIRMA
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        String ticketNumber = String.format("%04d %04d", pasaje.getId() / 1000, pasaje.getId() % 1000);
        String formattedPrice = String.format("€ %.2f", pasaje.getPrecio());

        // NOTA: El HTML y CSS son los mismos, solo cambia cómo se inserta el QR
        return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
            <title>Tu Pasaje de Bus</title>
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 0; background-color: #e9ecef; }
                .ticket-container { max-width: 800px; margin: 20px auto; box-shadow: 0 10px 25px rgba(0,0,0,0.1); border-radius: 12px; }
                .main-part { padding: 35px; background-color: #f8f9fa; border-top-left-radius: 12px; border-bottom-left-radius: 12px; }
                .stub-part { width: 280px; background-color: #ffffff; border-top-right-radius: 12px; border-bottom-right-radius: 12px; }
                .header h1 { font-size: 24px; font-weight: 600; color: #1a202c; margin: 0; }
                .header span { font-size: 14px; color: #718096; }
                .info-table td { padding: 6px 0; vertical-align: top; }
                .info-table strong { font-weight: 600; color: #4a5568; padding-right: 10px; }
                .info-table span { color: #2d3748; }
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
                         <!-- Para corregir el centrado en el PDF, envolvemos la imagen en un div centrado -->
                         <div style="text-align: center;">
                            <img src="%s" alt="QR Code" class="qr-code" width="180" height="180" style="width:180px; height:180px; margin-bottom:20px;" />
                         </div>
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
                qrCodeSrc, // <-- CAMBIO: Se usa el parámetro qrCodeSrc
                ticketNumber
        );
    }


    /**
     * NUEVO: Envía un correo de recordatorio de viaje a un pasajero.
     * @param pasaje El objeto Pasaje con toda la información necesaria.
     */
    public void sendDepartureReminderEmail(Pasaje pasaje) throws MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        // Obtenemos los objetos relacionados usando tus getters
        Viaje viaje = pasaje.getDatosViaje();
        Usuario cliente = pasaje.getCliente();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        helper.setFrom(fromEmail);
        helper.setTo(cliente.getEmail()); // <-- Obtenemos email del Usuario
        helper.setSubject("⏰ Recordatorio: Tu viaje a " + viaje.getDestino().getNombre() + " sale pronto");

        // Asumo que tu entidad Usuario tiene un método getNombre() o similar.
        // Si se llama diferente (ej. getNombreCompleto()), ajústalo aquí.
        String nombreCliente = cliente.getNombre();

        String htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; color: #333; }
                .container { padding: 20px; border: 1px solid #ddd; border-radius: 8px; max-width: 600px; margin: auto; }
                .header { font-size: 24px; color: #0056b3; }
                .details { margin-top: 20px; }
                .details p { margin: 5px 0; }
                .footer { margin-top: 25px; font-size: 12px; color: #777; }
            </style>
        </head>
        <body>
            <div class="container">
                <h1 class="header">¡Hola, %s!</h1>
                <p>Este es un recordatorio amistoso sobre tu próximo viaje.</p>
                <div class="details">
                    <p><strong>Destino:</strong> %s</p>
                    <p><strong>Origen:</strong> %s</p>
                    <p><strong>Fecha de Salida:</strong> %s</p>
                    <p><strong>Hora de Salida:</strong> %s</p>
                    <p><strong>Número de Asiento:</strong> %d</p>
                </div>
                <p>Por favor, asegúrate de llegar a la terminal con tiempo suficiente para el embarque.</p>
                <p>¡Te deseamos un excelente viaje!</p>
                <div class="footer">
                    <p>Equipo de Omnibus</p>
                </div>
            </div>
        </body>
        </html>
        """.formatted(
                nombreCliente,
                viaje.getDestino().getNombre(),
                viaje.getOrigen().getNombre(),
                viaje.getFechaHoraSalida().format(dateFormatter),
                viaje.getFechaHoraSalida().format(timeFormatter),
                pasaje.getNumeroAsiento() // <-- Usamos el getter de Pasaje
        );

        helper.setText(htmlContent, true);
        mailSender.send(mimeMessage);
        logger.info("Email de recordatorio de viaje enviado a {}", cliente.getEmail());
    }



    public void sendRefundConfirmationEmail(Pasaje pasaje, double montoReembolsado) throws MessagingException {
        // La línea que buscaba el pasaje por ID ha sido ELIMINADA.

        logger.info("Construyendo email de devolución para pasaje ID: {}", pasaje.getId()); // Usamos pasaje.getId()

        // El resto del código que usa el objeto 'pasaje' se mantiene igual.
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        Viaje viaje = pasaje.getDatosViaje();
        Usuario cliente = pasaje.getCliente();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String nombreCliente = cliente.getNombre();
        String montoFormateado = String.format(Locale.US, "%.2f", montoReembolsado);

        helper.setFrom(fromEmail);
        helper.setTo(cliente.getEmail());
        helper.setSubject("✅ Devolución procesada - Viaje a " + viaje.getDestino().getNombre());

        // 6. Construir el cuerpo HTML del correo.
        String htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; color: #333; line-height: 1.6; }
                .container { padding: 20px; border: 1px solid #ddd; border-radius: 8px; max-width: 600px; margin: auto; background-color: #f9f9f9; }
                .header { font-size: 24px; color: #1a73e8; font-weight: bold; }
                .details { margin-top: 20px; padding: 15px; background-color: #ffffff; border-left: 4px solid #1a73e8; }
                .details h3 { margin-top: 0; color: #333; }
                .details p { margin: 5px 0; }
                .highlight { margin-top: 15px; padding: 10px; background-color: #fffbe6; border: 1px solid #ffe58f; border-radius: 4px; font-size: 14px; }
                .footer { margin-top: 25px; font-size: 12px; color: #777; text-align: center; }
            </style>
        </head>
        <body>
            <div class="container">
                <h1 class="header">Confirmación de Devolución</h1>
                <p>Hola, %s,</p>
                <p>Te confirmamos que la devolución de tu pasaje ha sido procesada exitosamente.</p>
                
                <div class="details">
                    <h3>Detalles del Pasaje Devuelto</h3>
                    <p><strong>Destino:</strong> %s</p>
                    <p><strong>Origen:</strong> %s</p>
                    <p><strong>Fecha de Salida:</strong> %s</p>
                    <p><strong>Número de Asiento:</strong> %d</p>
                </div>

                <div class="details">
                    <h3>Detalles del Reembolso</h3>
                    <p>Hemos procesado un reembolso a tu cuenta de PayPal.</p>
                    <p><strong>Monto Reembolsado:</strong> $%s USD</p>
                    <div class="highlight">
                        <strong>Importante:</strong> El reembolso puede tardar de 3 a 5 días hábiles en aparecer en tu estado de cuenta, dependiendo de los tiempos de procesamiento de PayPal y tu banco.
                    </div>
                </div>
                
                <p>Lamentamos que no puedas viajar con nosotros en esta ocasión y esperamos verte pronto.</p>

                <div class="footer">
                    <p>Equipo de Omnibus</p>
                </div>
            </div>
        </body>
        </html>
        """.formatted(
                nombreCliente,
                viaje.getDestino().getNombre(),
                viaje.getOrigen().getNombre(),
                viaje.getFechaHoraSalida().format(dateFormatter),
                pasaje.getNumeroAsiento(),
                montoFormateado
        );

        // 7. Asignar el contenido y enviar.
        helper.setText(htmlContent, true);
        mailSender.send(mimeMessage);

        logger.info("Email de confirmación de devolución enviado exitosamente a {} para pasaje ID {}", cliente.getEmail(), pasaje.getId());
    }
}

