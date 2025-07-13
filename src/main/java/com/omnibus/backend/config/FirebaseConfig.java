package com.omnibus.backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp() throws IOException {
        if (FirebaseApp.getApps().isEmpty()) {
            try {
                GoogleCredentials credentials;
                
                // 🔥 INTENTAR CARGAR DESDE VARIABLE DE ENTORNO PRIMERO
                String firebaseJson = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
                
                if (firebaseJson != null && !firebaseJson.isEmpty()) {
                    // Producción: usar variable de entorno
                    logger.info("Cargando credenciales Firebase desde variable de entorno");
                    InputStream credentialsStream = new ByteArrayInputStream(firebaseJson.getBytes());
                    credentials = GoogleCredentials.fromStream(credentialsStream);
                } else {
                    // Desarrollo: usar archivo local
                    logger.info("Cargando credenciales Firebase desde archivo local");
                    InputStream serviceAccount = new ClassPathResource("firebase-service-account.json").getInputStream();
                    credentials = GoogleCredentials.fromStream(serviceAccount);
                }
                
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(credentials)
                        .build();

                FirebaseApp app = FirebaseApp.initializeApp(options);
                logger.info("Firebase inicializado correctamente");
                return app;
            } catch (IOException e) {
                logger.error("Error al inicializar Firebase: {}", e.getMessage());
                throw e;
            }
        }
        return FirebaseApp.getInstance();
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}