package com.omnibus.backend.service;

import com.omnibus.backend.dto.PasajeResponseDTO;
import com.omnibus.backend.service.EmailService;
import com.omnibus.backend.service.QrCodeService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class EmailServiceTest {

    @Mock private JavaMailSender mailSender;
    @Mock private QrCodeService qrCodeService;
    @Mock private MimeMessage mimeMessage;

    @InjectMocks private EmailService emailService;

    private PasajeResponseDTO pasaje;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        pasaje = new PasajeResponseDTO();
        pasaje.setId(1234);
        pasaje.setClienteNombre("Juan Perez");
        pasaje.setClienteEmail("juan@example.com");
        pasaje.setOmnibusMatricula("ABC123");
        pasaje.setNumeroAsiento(12);
        pasaje.setPrecio(500.0);
        pasaje.setOrigenViaje("Montevideo");
        pasaje.setDestinoViaje("Colonia");
        pasaje.setFechaViaje(LocalDate.now());
        pasaje.setHoraSalidaViaje(LocalTime.of(10, 30));

        // Set mock values for properties
        emailService = new EmailService(mailSender, qrCodeService);

        // Establecer valores por defecto para las variables @Value
        emailService.setFrontendUrl("http://localhost:4200");
        emailService.setFromEmail("noreply@omnibus.com");
    }

    @Test
    void testSendPasswordResetEmail_noException() {
        doNothing().when(mailSender).send((MimeMessage) any());
        assertDoesNotThrow(() -> emailService.sendPasswordResetEmail("user@example.com", "token123"));
    }

    @Test
    void testBuildAndSendTicket_success() throws Exception {
        byte[] qrBytes = new byte[]{1, 2, 3};
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(qrCodeService.generateQrCodeImage(anyString(), anyInt(), anyInt())).thenReturn(qrBytes);

        assertDoesNotThrow(() -> emailService.buildAndSendTicket(pasaje));

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void testBuildAndSendTicket_qrFails() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(mock(MimeMessage.class));
        when(qrCodeService.generateQrCodeImage(anyString(), anyInt(), anyInt())).thenThrow(new IOException("Error QR"));

        assertThrows(IOException.class, () -> emailService.buildAndSendTicket(pasaje));
    }
}
