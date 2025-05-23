package com.omnibus.backend.controller;

import com.omnibus.backend.dto.*;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.security.JwtUtil;
import com.omnibus.backend.service.CustomUserDetailsService;
import com.omnibus.backend.service.UserService; // <-- PASO 1: IMPORTA UserService
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
// Cambiamos el RequestMapping base si estos endpoints no son estrictamente de "auth"
// Podrías tener /auth para login/register y /api/user para profile, o mantenerlos juntos si prefieres.
// Si los endpoints /profile se quedan aquí, el @RequestMapping("/auth") está bien.
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
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

    @Autowired // <-- PASO 2: INYECTA UserService
    private UserService userService;

    // ... (método /register sin cambios) ...
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) {
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body("El email ya está registrado.");
        }
        String rolParaGuardar = dto.rol;
        if (rolParaGuardar == null || rolParaGuardar.trim().isEmpty()) {
            rolParaGuardar = "ROLE_USER";
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
        return ResponseEntity.ok("Usuario registrado exitosamente.");
    }


    // ... (método /login sin cambios) ...
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
        if (userDetails instanceof Usuario) {
            Usuario usuario = (Usuario) userDetails;
            nombreUsuario = usuario.getNombre();
            apellidoUsuario = usuario.getApellido();
            String rolCompleto = usuario.getRol();
            if (rolCompleto != null && rolCompleto.startsWith("ROLE_")) {
                rolParaFrontend = rolCompleto.substring(5).toLowerCase();
            } else {
                rolParaFrontend = rolCompleto != null ? rolCompleto.toLowerCase() : "";
            }
        } else {
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
                userDetails.getUsername(),
                rolParaFrontend,
                nombreUsuario,
                apellidoUsuario
        ));
    }


    // Los métodos /profile ahora usarán el userService inyectado
    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName();

        try {
            UserProfileDTO userProfile = userService.getUserProfileByEmail(currentUsername); // Ahora 'userService' está disponible
            return ResponseEntity.ok(userProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(404).body("Usuario no encontrado.");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error al obtener el perfil del usuario.");
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody UpdateUserDTO updateUserDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName();

        try {
            UserProfileDTO updatedUserProfile = userService.updateUserProfile(currentUsername, updateUserDTO); // Ahora 'userService' está disponible
            return ResponseEntity.ok(updatedUserProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(404).body("Usuario no encontrado.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace(); // Es buena idea loguear la traza completa del error en el servidor
            return ResponseEntity.status(500).body("Error interno al actualizar el perfil.");
        }
    }
}