// src/main/java/com/omnibus/backend/config/SecurityConfig.java
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
@EnableMethodSecurity(prePostEnabled = true)
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
                "http://localhost:3000",
                "https://frontend-eosin-eight-41.vercel.app",
                "https://frontend-pjqhx2c7u-santi53197442s-projects.vercel.app",
                "https://frontend-git-master-santi53197442s-projects.vercel.app",
                "https://frontend-4f1xmt5az-santi53197442s-projects.vercel.app",
                "https://frontend-kndjuqyhd-santi53197442s-projects.vercel.app"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authz -> authz
                        // --- PÚBLICO Y OPCIONES ---
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll() // Preflight CORS
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()

                        .requestMatchers("/api/paypal/**").permitAll() //PARA PAYPAL

                        // ================== CAMBIO AQUÍ ==================
                        // AÑADE ESTA LÍNEA PARA HACER EL ENDPOINT PÚBLICO
                        .requestMatchers(HttpMethod.GET, "/api/vendedor/localidades-disponibles").permitAll()
                        // ===============================================

                        // --- ENDPOINTS ACCESIBLES POR CLIENTES, VENDEDORES Y ADMINS (DENTRO DE /api/vendedor) ---
                        // ELIMINA la regla de aquí, ya que la movimos arriba para que sea pública.
                        // .requestMatchers(HttpMethod.GET, "/api/vendedor/localidades-disponibles")
                        // .hasAnyRole("CLIENTE", "VENDEDOR", "ADMINISTRADOR")

                        .requestMatchers(HttpMethod.GET, "/api/vendedor/viajes/buscar-disponibles")
                        .hasAnyRole("CLIENTE", "VENDEDOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/vendedor/viajes/*/detalles-asientos")
                        .hasAnyRole("CLIENTE", "VENDEDOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, "/api/vendedor/viajes/*/asientos-ocupados")
                        .hasAnyRole("CLIENTE", "VENDEDOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/vendedor/pasajes/comprar")
                        .hasAnyRole("CLIENTE", "VENDEDOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/vendedor/pasajes/comprar-multiple")
                        .hasAnyRole("CLIENTE", "VENDEDOR", "ADMINISTRADOR")
                        .requestMatchers(HttpMethod.POST, "/api/vendedor/pasajes/reservar-temporalmente")
                        .hasAnyRole("CLIENTE", "VENDEDOR", "ADMINISTRADOR")

                        // --- ENDPOINTS DE ADMINISTRADOR ---
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")

                        // --- ENDPOINTS DE VENDEDOR (LO RESTANTE BAJO /api/vendedor) ---
                        .requestMatchers("/api/vendedor/**").hasAnyRole("VENDEDOR", "ADMINISTRADOR")

                        // --- ENDPOINTS DE USUARIO AUTENTICADO (GENÉRICOS) ---
                        .requestMatchers(HttpMethod.GET, "/api/user/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/user/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/user/password").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/user/ci/*").authenticated()

                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
