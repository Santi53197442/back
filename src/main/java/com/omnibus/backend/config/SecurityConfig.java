package com.omnibus.backend.config;

import com.omnibus.backend.security.JwtRequestFilter;
// No es necesario importar CustomUserDetailsService aquí si solo se usa para configurar el AuthenticationManager globalmente
// import com.omnibus.backend.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod; // Importar HttpMethod
import org.springframework.security.authentication.AuthenticationManager;
// import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder; // No se usa si se configura de otra forma
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
// import org.springframework.web.cors.CorsConfiguration; // Para configuración de CORS más detallada
// import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
// import org.springframework.web.filter.CorsFilter;
// import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // @Autowired // CustomUserDetailsService se usa para configurar el AuthenticationManager global
    // private CustomUserDetailsService customUserDetailsService; // No se inyecta directamente aquí si no se usa para configurar HttpSecurity directamente

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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // .cors(Customizer.withDefaults()) // Si tienes un @Bean CorsConfigurationSource
                // O, si usas @CrossOrigin en los controladores, esto podría no ser necesario o necesitar configuración diferente.
                // Por ahora, con @CrossOrigin en controladores, podemos omitir .cors() aquí o usarlo si tienes un bean CorsFilter.
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers("/auth/register", "/auth/login").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Rutas para el perfil del usuario, requieren autenticación
                        .requestMatchers(HttpMethod.GET, "/api/user/profile").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/user/profile").authenticated()
                        // Podrías generalizarlo si tienes más endpoints bajo /api/user:
                        // .requestMatchers("/api/user/**").authenticated()
                        .anyRequest().authenticated() // Todas las demás requieren autenticación
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        http.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Opcional: Bean para una configuración de CORS más global si no usas @CrossOrigin en cada controlador
    /*
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true); // Permitir cookies/auth headers
        config.addAllowedOrigin("http://localhost:3000"); // Tu frontend en desarrollo
        config.addAllowedOrigin("https://tu-frontend-en-vercel.app"); // Tu frontend en producción
        config.addAllowedHeader("*"); // Permitir todas las cabeceras
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // Métodos permitidos
        source.registerCorsConfiguration("/**", config); // Aplicar a todas las rutas
        return new CorsFilter(source);
    }
    */
}