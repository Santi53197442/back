package com.omnibus.backend.controller;

import com.omnibus.backend.dto.AuthResponseDTO;
import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
import com.omnibus.backend.model.Usuario; // Asegúrate que esta es tu entidad Usuario
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.security.JwtUtil;
import com.omnibus.backend.service.CustomUserDetailsService; // Tu servicio de UserDetails
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*") // Permite CORS, ajusta a tus dominios de frontend en producción
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private CustomUserDetailsService userDetailsService; // Asumo que este es tu UserDetailsService

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) {
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body("El email ya está registrado.");
        }

        // Manejo del rol para asegurar el formato "ROLE_..." en la base de datos
        String rolParaGuardar = dto.rol;
        if (rolParaGuardar == null || rolParaGuardar.trim().isEmpty()) {
            rolParaGuardar = "ROLE_USER"; // Rol por defecto si no se especifica
        } else if (!rolParaGuardar.startsWith("ROLE_")) {
            rolParaGuardar = "ROLE_" + rolParaGuardar.toUpperCase();
        }

        Usuario usuario = new Usuario(
                dto.nombre,
                dto.apellido,
                dto.ci,
                passwordEncoder.encode(dto.contrasenia), // Contraseña codificada
                dto.email,
                dto.telefono,
                dto.fechaNac,
                rolParaGuardar // Rol con prefijo "ROLE_"
        );
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("Usuario registrado exitosamente.");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO dto) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email, dto.contrasenia)
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Credenciales incorrectas.");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(dto.email);
        final String token = jwtUtil.generateToken(userDetails);

        String nombreUsuario = "";
        String apellidoUsuario = "";
        String rolParaFrontend = "";

        // Intenta obtener los datos directamente si UserDetails es una instancia de tu Usuario
        if (userDetails instanceof Usuario) {
            Usuario usuario = (Usuario) userDetails;
            nombreUsuario = usuario.getNombre();
            apellidoUsuario = usuario.getApellido();
            String rolCompleto = usuario.getRol(); // Ej. "ROLE_ADMIN"

            // Convertir el rol para el frontend (ej. "admin")
            if (rolCompleto != null && rolCompleto.startsWith("ROLE_")) {
                rolParaFrontend = rolCompleto.substring(5).toLowerCase();
            } else {
                rolParaFrontend = rolCompleto != null ? rolCompleto.toLowerCase() : "";
            }

        } else {
            // Fallback: si UserDetails no es tu clase Usuario, carga el Usuario desde la BD
            // Esto no debería ser necesario si CustomUserDetailsService devuelve tu entidad Usuario
            // que implementa UserDetails.
            Usuario usuarioFromDb = usuarioRepository.findByEmail(userDetails.getUsername()).orElse(null);
            if (usuarioFromDb != null) {
                nombreUsuario = usuarioFromDb.getNombre();
                apellidoUsuario = usuarioFromDb.getApellido();
                String rolCompleto = usuarioFromDb.getRol();
                if (rolCompleto != null && rolCompleto.startsWith("ROLE_")) {
                    rolParaFrontend = rolCompleto.substring(5).toLowerCase();
                } else {
                    rolParaFrontend = rolCompleto != null ? rolCompleto.toLowerCase() : "";
                }
            }
        }

        return ResponseEntity.ok(new AuthResponseDTO(
                token,
                userDetails.getUsername(), // Esto es el email
                rolParaFrontend,           // Rol formateado para el frontend
                nombreUsuario,
                apellidoUsuario
        ));
    }
}