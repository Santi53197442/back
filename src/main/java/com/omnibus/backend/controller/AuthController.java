package com.omnibus.backend.controller;

import com.omnibus.backend.dto.AuthResponseDTO;
import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.security.JwtUtil;
import com.omnibus.backend.service.CustomUserDetailsService;
// Ya no se necesita UserService aquí si solo se usa en UserController
// import com.omnibus.backend.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// Ya no se necesita Authentication ni SecurityContextHolder aquí para /profile
// import org.springframework.security.core.Authentication;
// import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
// import org.springframework.security.core.userdetails.UsernameNotFoundException; // Ya no se usa aquí
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth") // Solo para /register y /login
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

    // UserService ya no se inyecta aquí si no se usa en este controlador
    // @Autowired
    // private UserService userService;

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
        String ciUsuario = "";
        String telefonoUsuario = "";
        String fechaNacUsuario = "";

        if (userDetails instanceof Usuario) {
            Usuario usuario = (Usuario) userDetails;
            nombreUsuario = usuario.getNombre();
            apellidoUsuario = usuario.getApellido();
            ciUsuario = usuario.getCi() != null ? String.valueOf(usuario.getCi()) : "";
            telefonoUsuario = usuario.getTelefono() != null ? String.valueOf(usuario.getTelefono()) : "";
            if (usuario.getFechaNac() != null) {
                fechaNacUsuario = usuario.getFechaNac().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
            }
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
                ciUsuario = usuarioFromDb.getCi() != null ? String.valueOf(usuarioFromDb.getCi()) : "";
                telefonoUsuario = usuarioFromDb.getTelefono() != null ? String.valueOf(usuarioFromDb.getTelefono()) : "";
                if (usuarioFromDb.getFechaNac() != null) {
                    fechaNacUsuario = usuarioFromDb.getFechaNac().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE);
                }
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
                apellidoUsuario,
                ciUsuario,
                telefonoUsuario,
                fechaNacUsuario
        ));
    }

    // LOS MÉTODOS getCurrentUserProfile() Y updateUserProfile() SE HAN MOVIDO A UserController.java
}