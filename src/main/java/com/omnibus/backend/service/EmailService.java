package com.omnibus.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void sendPasswordResetEmail(String to, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Restablecimiento de Contraseña");
        String resetUrl = frontendUrl + "/reset-password?token=" + token;
        message.setText("Hola,\n\nHas solicitado restablecer tu contraseña.\n" +
                "Haz clic en el siguiente enlace para continuar:\n" + resetUrl +
                "\n\nSi no solicitaste esto, ignora este correo.\n" +
                "El enlace expirará en 1 hora.\n\nSaludos,\nTu Aplicación");
        try {
            mailSender.send(message);
            System.out.println("Correo de restablecimiento enviado a: " + to);
        } catch (Exception e) {
            System.err.println("Error al enviar correo de restablecimiento a " + to + ": " + e.getMessage());
            // Considera un logging más robusto aquí
        }
    }
}
