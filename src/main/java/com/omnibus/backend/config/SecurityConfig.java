package com.omnibus.backend.config;

import com.omnibus.backend.security.JwtRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Habilita @PreAuthorize, etc.
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000", // Desarrollo local
                "https://frontend-eosin-eight-41.vercel.app", // Reemplaza con tus URLs de Vercel reales
                "https://frontend-pjqhx2c7u-santi53197442s-projects.vercel.app",
                "https://frontend-git-master-santi53197442s-projects.vercel.app",
                "https://frontend-4f1xmt5az-santi53197442s-projects.vercel.app",
                "https://frontend-kndjuqyhd-santi53197442s-projects.vercel.app"
                // Añade cualquier otra URL de producción aquí
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*")); // Para producción, considera ser más específico (ej: "Authorization", "Content-Type")
        configuration.setAllowCredentials(true);
        // configuration.setMaxAge(3600L); // Opcional: cuánto tiempo el navegador puede cachear la respuesta preflight

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Aplica esta configuración CORS a todas las rutas
        return source;
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // Aplica la configuración CORS definida arriba
                .csrf(csrf -> csrf.disable()) // Deshabilita CSRF ya que se usa JWT (stateless)
                .authorizeHttpRequests(authz -> authz
                        // PERMITIR SOLICITUDES OPTIONS GLOBALMENTE (importante para CORS preflight)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Endpoints públicos (no requieren autenticación)
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/**").permitAll() // Para health checks, etc. (considera restringir en producción)
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()

                        // Endpoints de Administrador
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR") // Solo usuarios con rol ADMINISTRADOR

                        // --- INICIO DE LA SECCIÓN MODIFICADA PARA VENDEDOR/CLIENTE/ADMIN ---
                        // Regla específica para el endpoint de detalles-asientos:
                        // Debe ir ANTES de la regla general para /api/vendedor/**
                        .requestMatchers(HttpMethod.GET, "/api/vendedor/viajes/*/detalles-asientos")
                        .hasAnyRole("VENDEDOR", "ADMINISTRADOR", "CLIENTE") // Usuarios con cualquiera de estos roles

                        // Endpoints generales de Vendedor (lo que queda bajo /api/vendedor/**):
                        // Esta regla se aplica si la anterior más específica no coincidió.
                        // Ajusta los roles aquí según sea necesario.
                        // Si la mayoría de las otras rutas /api/vendedor/* son solo para VENDEDOR, usa .hasRole("VENDEDOR")
                        // Si los ADMIN también pueden acceder a todo lo de VENDEDOR, usa .hasAnyRole("VENDEDOR", "ADMINISTRADOR")
                        .requestMatchers("/api/vendedor/**").hasAnyRole("VENDEDOR", "ADMINISTRADOR")
                        // --- FIN DE LA SECCIÓN MODIFICADA ---

                        // Endpoints de Cliente (si tienes rutas específicas para clientes fuera de /api/vendedor)
                        // Ejemplo: .requestMatchers("/api/cliente/**").hasRole("CLIENTE")

                        // Endpoints de Usuario Autenticado (genéricos, para cualquier usuario logueado)
                        .requestMatchers(HttpMethod.GET, "/api/user/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/user/profile").authenticated()

                        // Todas las demás solicitudes no especificadas arriba deben estar autenticadas
                        .anyRequest().authenticated()
                )
                // Configuración de sesión: STATELESS ya que se usa JWT
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Añade el filtro JWT antes del filtro de autenticación estándar de Spring
        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}