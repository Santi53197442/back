package com.omnibus.backend.controller;

import com.omnibus.backend.dto.AuthResponseDTO;
import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.security.JwtUtil;
import com.omnibus.backend.service.CustomUserDetailsService;
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
public class AuthController {

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

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) {
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body("Email ya registrado");
        }

        // IMPORTANTE: Asegúrate que el rol en RegisterDTO sea como "ROLE_USER", "ROLE_ADMIN"
        // Si no, ajusta aquí o en el modelo Usuario.
        String rol = dto.rol;
        if (rol == null || rol.trim().isEmpty()) {
            rol = "ROLE_USER"; // Rol por defecto si no se especifica
        } else if (!rol.startsWith("ROLE_")) {
            rol = "ROLE_" + rol.toUpperCase(); // Asegurar formato ROLE_
        }


        Usuario usuario = new Usuario(
                dto.nombre, dto.apellido, dto.ci,
                passwordEncoder.encode(dto.contrasenia), // Codificar contraseña
                dto.email, dto.telefono, dto.fechaNac, rol
        );
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("Registro exitoso");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO dto) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.email, dto.contrasenia)
            );
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).body("Credenciales incorrectas");
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(dto.email);
        final String token = jwtUtil.generateToken(userDetails);

        String userRol = "";
        if (userDetails instanceof Usuario) {
            userRol = ((Usuario) userDetails).getRol();
        }

        return ResponseEntity.ok(new AuthResponseDTO(token, userDetails.getUsername(), userRol));
    }
}