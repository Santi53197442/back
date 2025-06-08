package com.omnibus.backend.controller;

import com.omnibus.backend.dto.AuthResponseDTO;
import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
// Importa las subclases si las vas a usar explícitamente con instanceof
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.security.JwtUtil;
import com.omnibus.backend.service.CustomUserDetailsService;
import com.omnibus.backend.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import com.omnibus.backend.model.Administrador; // Importa la clase Administrador


import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UsuarioRepository usuarioRepository; // Sigue siendo UsuarioRepository

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService; // Devuelve UserDetails (que será Cliente, Vendedor, etc.)

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserService userService; // Para forgot-password y reset-password

    // Clases internas para las solicitudes de reseteo (se mantienen igual)
    static class EmailRequest { public String email; }
    static class ResetPasswordRequest { public String token; public String newPassword; }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) {
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El email ya está registrado."));
        }
        // ...resto de validaciones...
        if (dto.nombre == null || dto.nombre.trim().isEmpty() || /* ... */ dto.fechaNac == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos son requeridos."));
        }

        // --- CAMBIO PRINCIPAL AQUÍ ---
        // Ahora creamos el cliente y le pasamos el tipo por defecto "COMUN".
        Cliente nuevoCliente = new Cliente(
                dto.nombre,
                dto.apellido,
                dto.ci,
                passwordEncoder.encode(dto.contrasenia),
                dto.email,
                dto.telefono,
                dto.fechaNac,
                TipoCliente.COMUN // <-- ¡AQUÍ ESTÁ LA MAGIA!
        );
        // ----------------------------

        try {
            usuarioRepository.save(nuevoCliente);
            return ResponseEntity.ok(Map.of("message", "Usuario Cliente registrado exitosamente."));
        } catch (Exception e) {
            logger.error("Error al registrar nuevo cliente: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error al registrar el usuario."));
        }
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

        Usuario usuarioAutenticado = (Usuario) userDetails; // Tu clase base Usuario DEBE tener un método getId()

        String rolParaFrontend = "desconocido";
        if (usuarioAutenticado instanceof Administrador) {
            rolParaFrontend = "administrador";
        } else if (usuarioAutenticado instanceof Vendedor) {
            rolParaFrontend = "vendedor";
        } else if (usuarioAutenticado instanceof Cliente) {
            rolParaFrontend = "cliente";
        }

        // --- CAMBIO AQUÍ para incluir el ID ---
        return ResponseEntity.ok(new AuthResponseDTO(
                token,
                usuarioAutenticado.getId(), // <<<--- AÑADIR ESTO
                usuarioAutenticado.getEmail(),
                rolParaFrontend,
                usuarioAutenticado.getNombre(),
                usuarioAutenticado.getApellido(),
                usuarioAutenticado.getCi() != null ? String.valueOf(usuarioAutenticado.getCi()) : "",
                usuarioAutenticado.getTelefono() != null ? String.valueOf(usuarioAutenticado.getTelefono()) : "",
                usuarioAutenticado.getFechaNac() != null ? usuarioAutenticado.getFechaNac().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) : ""
        ));
        // ------------------------------------
    }

    // Endpoints de forgot-password y reset-password se mantienen igual
    // ya que operan sobre 'UsuarioBase' (ahora 'Usuario') a través de 'UserService'.
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody EmailRequest emailRequest) {
        // ... (sin cambios aquí, ya que userService maneja la lógica con UsuarioRepository) ...
        try {
            if (emailRequest.email == null || emailRequest.email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El email es requerido."));
            }
            userService.requestPasswordReset(emailRequest.email);
            return ResponseEntity.ok(Map.of("message", "Si tu correo está registrado, recibirás un enlace para restablecer tu contraseña en breve."));
        } catch (Exception e) {
            logger.error("Error en /forgot-password para email {}: {}", emailRequest.email, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Ocurrió un error procesando tu solicitud."));
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest resetRequest) {
        // ... (sin cambios aquí) ...
        if (resetRequest.token == null || resetRequest.token.trim().isEmpty() ||
                resetRequest.newPassword == null || resetRequest.newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Token y nueva contraseña son requeridos."));
        }
        boolean success = userService.resetPassword(resetRequest.token, resetRequest.newPassword);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "Contraseña restablecida exitosamente."));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", "El enlace de restablecimiento es inválido o ha expirado. Por favor, solicita uno nuevo."));
        }
    }
}