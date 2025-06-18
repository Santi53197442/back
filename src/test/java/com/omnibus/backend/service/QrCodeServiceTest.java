package com.omnibus.backend.service;

import com.google.zxing.WriterException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    void testGenerateQrCodeImage_success() throws WriterException, IOException {
        String text = "Test QR Code";
        int width = 200;
        int height = 200;

        byte[] imageBytes = qrCodeService.generateQrCodeImage(text, width, height);

        assertNotNull(imageBytes);
        assertTrue(imageBytes.length > 0);
    }

    @Test
    void testGenerateQrCodeImage_invalidSize_shouldThrowException() {
        String text = "Invalid QR Size";
        int width = -100;
        int height = -100;

        assertThrows(IllegalArgumentException.class, () ->
                qrCodeService.generateQrCodeImage(text, width, height));
    }

    @Test
    void testGenerateQrCodeImage_nullText_shouldThrowException() {
        assertThrows(NullPointerException.class, () ->
                qrCodeService.generateQrCodeImage(null, 200, 200));
    }
}
