package com.omnibus.backend.controller;

import com.omnibus.backend.dto.AuthResponseDTO;
import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
// Importa las subclases si las vas a usar explícitamente con instanceof
import com.omnibus.backend.model.Administrador;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Usuario; // Clase base
import com.omnibus.backend.model.Vendedor;
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
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) { // RegisterDTO puede seguir igual
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El email ya está registrado."));
        }
        // Validaciones básicas (se mantienen)
        if (dto.nombre == null || dto.nombre.trim().isEmpty() || /* ...resto de validaciones... */ dto.fechaNac == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Todos los campos son requeridos."));
        }

        // Para el registro público, siempre creamos un Cliente.
        // Los campos específicos de Cliente (si los tuviera y vinieran del DTO) se pasarían aquí.
        Cliente nuevoCliente = new Cliente(
                dto.nombre,
                dto.apellido,
                dto.ci,
                passwordEncoder.encode(dto.contrasenia), // Hashear contraseña
                dto.email,
                dto.telefono,
                dto.fechaNac
                // , 0 // Ejemplo si Cliente tuviera 'puntosFidelidad' iniciales
        );

        try {
            usuarioRepository.save(nuevoCliente); // Guardamos la instancia de Cliente
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

        // userDetails ya es una instancia de Cliente, Vendedor o Administrador.
        // Lo casteamos a Usuario (clase base) para acceder a campos comunes.
        Usuario usuarioAutenticado = (Usuario) userDetails;

        String rolParaFrontend = "desconocido"; // Rol por defecto
        // Determinar el rol basado en el tipo de instancia
        if (usuarioAutenticado instanceof Administrador) {
            rolParaFrontend = "administrador";
        } else if (usuarioAutenticado instanceof Vendedor) {
            rolParaFrontend = "vendedor";
        } else if (usuarioAutenticado instanceof Cliente) {
            rolParaFrontend = "cliente";
        }
        // Alternativamente, podrías obtenerlo de userDetails.getAuthorities() si solo hay un rol
        // y formatearlo, pero instanceof es más directo si el tipo de clase define el rol principal.

        return ResponseEntity.ok(new AuthResponseDTO(
                token,
                usuarioAutenticado.getEmail(),
                rolParaFrontend, // String del rol determinado
                usuarioAutenticado.getNombre(),
                usuarioAutenticado.getApellido(),
                usuarioAutenticado.getCi() != null ? String.valueOf(usuarioAutenticado.getCi()) : "",
                usuarioAutenticado.getTelefono() != null ? String.valueOf(usuarioAutenticado.getTelefono()) : "",
                usuarioAutenticado.getFechaNac() != null ? usuarioAutenticado.getFechaNac().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE) : ""
        ));
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