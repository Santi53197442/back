package com.omnibus.backend.config; // Asegúrate de que el paquete sea el correcto

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {

            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**") // Aplica a todas las rutas de tu backend
                        .allowedOrigins(
                                "https://frontend-one-pi-71.vercel.app",       // Tu dominio principal de Vercel
                                "https://frontend-1utjf2icl-nicoversels-projects.vercel.app", // El otro dominio de despliegue de Vercel
                                "http://localhost:3000"  // Ejemplo si desarrollas el frontend localmente en el puerto 3000
                                // "http://localhost:5173", // Ejemplo si usas Vite u otro en este puerto
                                // "http://localhost:8081"  // Otro ejemplo de puerto local
                                // Añade cualquier otro origen local desde el que pruebes tu frontend
                        )
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Métodos HTTP permitidos
                        .allowedHeaders("*") // Permite todas las cabeceras
                        .allowCredentials(true); // Importante si envías cookies o encabezados de autenticación
            }
        };
    }
}




