package com.omnibus.backend.controller;

import com.omnibus.backend.dto.AuthResponseDTO;
import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.security.JwtUtil;
import com.omnibus.backend.service.CustomUserDetailsService;
import com.omnibus.backend.service.UserService; // <-- Asegúrate que la importación sea correcta
import org.slf4j.Logger; // Para logging
import org.slf4j.LoggerFactory; // Para logging
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
// @CrossOrigin(origins = "*") // Es mejor usar la configuración CORS global en SecurityConfig
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class); // Para logging

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired // <-- INYECTAR USERSERVICE
    private UserService userService;

    // Clases internas para las solicitudes de reseteo
    static class EmailRequest {
        public String email;
    }
    static class ResetPasswordRequest {
        public String token;
        public String newPassword;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) {
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El email ya está registrado."));
        }
        // Validaciones básicas
        if (dto.nombre == null || dto.nombre.trim().isEmpty() ||
                dto.apellido == null || dto.apellido.trim().isEmpty() ||
                dto.ci == null ||
                dto.contrasenia == null || dto.contrasenia.trim().isEmpty() ||
                dto.email == null || dto.email.trim().isEmpty() ||
                dto.telefono == null ||
                dto.fechaNac == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos son requeridos."));
        }

        String rolParaGuardar = dto.rol;
        if (rolParaGuardar == null || rolParaGuardar.trim().isEmpty()) {
            rolParaGuardar = "ROLE_USER"; // O "ROLE_CLIENTE" si es tu default
        } else if (!rolParaGuardar.startsWith("ROLE_")) {
            rolParaGuardar = "ROLE_" + rolParaGuardar.toUpperCase();
        }
        Usuario usuario = new Usuario(
                dto.nombre,
                dto.apellido,
                dto.ci,
                passwordEncoder.encode(dto.contrasenia),
                dto.email,
                dto.telefono,
                dto.fechaNac,
                rolParaGuardar
        );
        usuarioRepository.save(usuario);
        return ResponseEntity.ok(Map.of("message", "Usuario registrado exitosamente."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO dto) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email, dto.contrasenia)
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Credenciales incorrectas."));
        }
        final UserDetails userDetails = userDetailsService.loadUserByUsername(dto.email);
        final String token = jwtUtil.generateToken(userDetails);

        Usuario usuario = usuarioRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado después de autenticación exitosa, esto no debería ocurrir."));


        String rolParaFrontend = "";
        if (usuario.getRol() != null && usuario.getRol().startsWith("ROLE_")) {
            rolParaFrontend = usuario.getRol().substring(5).toLowerCase();
        } else {
            rolParaFrontend = usuario.getRol() != null ? usuario.getRol().toLowerCase() : "";
        }

        return ResponseEntity.ok(new AuthResponseDTO(
                token,
                usuario.getEmail(),
                rolParaFrontend,
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCi() != null ? String.valueOf(usuario.getCi()) : "",
                usuario.getTelefono() != null ? String.valueOf(usuario.getTelefono()) : "",
                usuario.getFechaNac() != null ? usuario.getFechaNac().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) : ""
        ));
    }

    // --- ENDPOINTS PARA RECUPERACIÓN DE CONTRASEÑA ---

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody EmailRequest emailRequest) {
        try {
            if (emailRequest.email == null || emailRequest.email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El email es requerido."));
            }
            // Llamar al método de instancia de userService
            userService.requestPasswordReset(emailRequest.email);
            return ResponseEntity.ok(Map.of("message", "Si tu correo está registrado, recibirás un enlace para restablecer tu contraseña en breve."));
        } catch (Exception e) {
            logger.error("Error en /forgot-password para email {}: {}", emailRequest.email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Ocurrió un error procesando tu solicitud."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest resetRequest) {
        if (resetRequest.token == null || resetRequest.token.trim().isEmpty() ||
                resetRequest.newPassword == null || resetRequest.newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token y nueva contraseña son requeridos."));
        }
        // Aquí podrías añadir validación de la fortaleza de la nueva contraseña

        // Llamar al método de instancia de userService
        boolean success = userService.resetPassword(resetRequest.token, resetRequest.newPassword);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Contraseña restablecida exitosamente."));
        } else {
            // El mensaje de error específico (token inválido vs. expirado) se maneja mejor
            // devolviendo diferentes códigos de error o mensajes desde el servicio,
            // pero para simplificar, usamos un mensaje genérico de fallo de token aquí.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "El enlace de restablecimiento es inválido o ha expirado. Por favor, solicita uno nuevo."));
        }
    }
}