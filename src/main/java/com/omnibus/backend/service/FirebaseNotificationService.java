package com.omnibus.backend.service;

import com.google.firebase.messaging.*;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.model.Viaje;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FirebaseNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseNotificationService.class);
    
    @Autowired
    private FirebaseMessaging firebaseMessaging;
    
    @Autowired
    private UserService userService; // Agregar esta inyección para limpiar tokens inválidos

    /**
     * Envía notificación push de cierre de ventas a un cliente específico
     */
    public void sendVentasCerradasNotification(Pasaje pasaje) {
        Usuario usuario = pasaje.getCliente();
        Viaje viaje = pasaje.getDatosViaje();

        // 🔥 VERIFICAR QUE EL USUARIO SEA UN CLIENTE
        if (!(usuario instanceof Cliente)) {
            logger.warn("El usuario {} no es un Cliente. No se puede enviar notificación push.", 
                    usuario.getEmail());
            return;
        }
        
        // 🔥 HACER EL CAST SEGURO
        Cliente cliente = (Cliente) usuario;
        
        if (cliente.getFcmToken() == null || cliente.getFcmToken().isEmpty()) {
            logger.warn("Cliente {} no tiene token FCM registrado. No se puede enviar notificación push.", 
                       cliente.getEmail());
            return;
        }

        try {
            String titulo = "🚌 Ventas Cerradas - ¡Tu viaje está confirmado!";
            String cuerpo = String.format(
                "Las ventas para tu viaje %s → %s han cerrado. " +
                "Salida: %s. ¡No olvides llegar 15 minutos antes!",
                viaje.getOrigen().getNombre(),
                viaje.getDestino().getNombre(),
                viaje.getFechaHoraSalida().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            );

            // Datos adicionales para la notificación
            Map<String, String> data = new HashMap<>();
            data.put("type", "VENTAS_CERRADAS");
            data.put("viaje_id", viaje.getId().toString());
            data.put("pasaje_id", pasaje.getId().toString());
            data.put("origen", viaje.getOrigen().getNombre());
            data.put("destino", viaje.getDestino().getNombre());
            data.put("fecha_salida", viaje.getFechaHoraSalida().toString());
            data.put("asiento", pasaje.getNumeroAsiento().toString());

            Message message = Message.builder()
                    .setToken(cliente.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(titulo)
                            .setBody(cuerpo)
                            .build())
                    .putAllData(data)
                    .setAndroidConfig(AndroidConfig.builder()
                            .setNotification(AndroidNotification.builder()
                                    .setIcon("ic_bus")
                                    .setColor("#2196F3")
                                    .setSound("default")
                                    .setPriority(AndroidNotification.Priority.HIGH)
                                    .build())
                            .build())
                    .setApnsConfig(ApnsConfig.builder()
                            .setAps(Aps.builder()
                                    .setSound("default")
                                    .setBadge(1)
                                    .build())
                            .build())
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("Notificación push enviada exitosamente al cliente {} para el viaje {}. Response: {}", 
                       cliente.getEmail(), viaje.getId(), response);

        } catch (FirebaseMessagingException e) {
            logger.error("Error al enviar notificación push al cliente {} para el viaje {}: {}", 
                        cliente.getEmail(), viaje.getId(), e.getMessage());
            
            // Si el token es inválido, limpiarlo de la base de datos
            if (e.getMessage() != null && 
                (e.getMessage().contains("registration-token-not-registered") ||
                e.getMessage().contains("invalid-registration-token") ||
                e.getMessage().contains("not-found"))) {
                
                logger.warn("Token FCM inválido para cliente {}. Limpiando token de la base de datos.", 
                        cliente.getEmail());
                try {
                    userService.limpiarTokenFCM(cliente.getEmail());
                    logger.info("Token FCM inválido limpiado para cliente: {}", cliente.getEmail());
                } catch (Exception cleanupError) {
                    logger.error("Error al limpiar token FCM inválido: {}", cleanupError.getMessage());
                }
            }
        }
    }

    /**
     * Envía notificación push de devolución de pasaje
     */
    public void sendDevolucionPasajeNotification(Pasaje pasaje) {
        Usuario usuario = pasaje.getCliente();
        Viaje viaje = pasaje.getDatosViaje();

        // 🔥 VERIFICAR QUE EL USUARIO SEA UN CLIENTE
        if (!(usuario instanceof Cliente)) {
            logger.warn("El usuario {} no es un Cliente. No se puede enviar notificación push.", 
                    usuario.getEmail());
            return;
        }
        
        // 🔥 HACER EL CAST SEGURO
        Cliente cliente = (Cliente) usuario;
        
        if (cliente.getFcmToken() == null || cliente.getFcmToken().isEmpty()) {
            logger.warn("Cliente {} no tiene token FCM registrado.", cliente.getEmail());
            return;
        }

        try {
            String titulo = "✅ Devolución Confirmada";
            String cuerpo = String.format(
                "Tu pasaje para el viaje %s → %s ha sido devuelto exitosamente. " +
                "El reintegro se procesará en los próximos días.",
                viaje.getOrigen().getNombre(),
                viaje.getDestino().getNombre()
            );

            Map<String, String> data = new HashMap<>();
            data.put("type", "DEVOLUCION_PASAJE");
            data.put("viaje_id", viaje.getId().toString());
            data.put("pasaje_id", pasaje.getId().toString());

            Message message = Message.builder()
                    .setToken(cliente.getFcmToken())
                    .setNotification(Notification.builder()
                            .setTitle(titulo)
                            .setBody(cuerpo)
                            .build())
                    .putAllData(data)
                    .build();

            String response = firebaseMessaging.send(message);
            logger.info("Notificación de devolución enviada al cliente {}: {}", cliente.getEmail(), response);

        } catch (FirebaseMessagingException e) {
            logger.error("Error al enviar notificación de devolución al cliente {}: {}", 
                        cliente.getEmail(), e.getMessage());
        }
    }

    /**
     * Envía notificaciones a múltiples clientes (para uso futuro)
     */
    public void sendMulticastNotification(List<String> tokens, String titulo, String cuerpo, Map<String, String> data) {
        if (tokens.isEmpty()) {
            logger.warn("Lista de tokens vacía, no se enviarán notificaciones multicast.");
            return;
        }

        try {
            MulticastMessage message = MulticastMessage.builder()
                    .addAllTokens(tokens)
                    .setNotification(Notification.builder()
                            .setTitle(titulo)
                            .setBody(cuerpo)
                            .build())
                    .putAllData(data)
                    .build();

            BatchResponse response = firebaseMessaging.sendMulticast(message);
            logger.info("Notificaciones multicast enviadas. Exitosas: {}, Fallidas: {}", 
                       response.getSuccessCount(), response.getFailureCount());

        } catch (FirebaseMessagingException e) {
            logger.error("Error al enviar notificaciones multicast: {}", e.getMessage());
        }
    }
}