package com.omnibus.backend.service;

import com.omnibus.backend.dto.PasajeResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

import java.io.IOException;

import static org.mockito.Mockito.*;

class AsyncServiceTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AsyncService asyncService;

    private PasajeResponseDTO pasaje;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Creamos un DTO mínimo con un id
        pasaje = new PasajeResponseDTO();
        pasaje.setId(42);
        // Si buildAndSendTicket usa más campos se agregan aca
    }

    @Test
    void sendTicketEmailAsync_delegatesToEmailService() throws Exception {
        // Preparamos buildAndSendTicket para que no lance ninguna excepción
        doNothing().when(emailService).buildAndSendTicket(pasaje);

        // Llamamos al métoddo (es síncrono en este test)
        asyncService.sendTicketEmailAsync(pasaje);

        // Verificamos que delegó la llamada exactamente una vez
        verify(emailService, times(1)).buildAndSendTicket(pasaje);
    }

    @Test
    void sendTicketEmailAsync_swallowExceptions() throws Exception {
        // Simulamos que buildAndSendTicket lanza una IOException
        doThrow(new IOException("io-fail"))
                .when(emailService).buildAndSendTicket(pasaje);

        // Al invocar no queremos que la excepción se propague al test
        asyncService.sendTicketEmailAsync(pasaje);

        // Aun así debe haber intentado enviar el email
        verify(emailService, times(1)).buildAndSendTicket(pasaje);
    }
}
