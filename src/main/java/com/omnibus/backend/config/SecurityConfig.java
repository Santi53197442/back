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
        // Asegúrate de que estas URLs sean las correctas y definitivas de tu frontend
        configuration.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://frontend-eosin-eight-41.vercel.app", // Ejemplo, reemplaza con tus URLs reales
                "https://frontend-pjqhx2c7u-santi53197442s-projects.vercel.app", // Ejemplo
                "https://frontend-git-master-santi53197442s-projects.vercel.app", // Ejemplo
                "https://frontend-4f1xmt5az-santi53197442s-projects.vercel.app", // Ejemplo
                "https://frontend-kndjuqyhd-santi53197442s-projects.vercel.app" // Ejemplo
                // Añade tu URL de producción final de Vercel aquí también si es diferente
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*")); // Para producción, considera ser más específico
        configuration.setAllowCredentials(true);
        // configuration.setMaxAge(3600L); // Opcional

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
                        // PERMITIR SOLICITUDES OPTIONS GLOBALMENTE PARA PREFLIGHT
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Endpoints públicos
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()

                        // Endpoints de Administrador
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")

                        // Endpoints de Vendedores - MODIFICADO
                        .requestMatchers("/api/vendedor/**").hasRole("VENDEDOR")


                        // Endpoints de Usuario Autenticado
                        .requestMatchers(HttpMethod.GET, "/api/user/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/user/profile").authenticated()

                        // Todas las demás solicitudes deben estar autenticadas
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}