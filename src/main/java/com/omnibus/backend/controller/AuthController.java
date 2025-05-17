package com.omnibus.backend.controller;

import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "https://frontend-eosin-eight-41.vercel.app")
public class AuthController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterDTO dto) {
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body("Email ya registrado");
        }

        Usuario usuario = new Usuario(
                dto.nombre, dto.apellido, dto.ci, dto.contrasenia, dto.email,
                dto.telefono, dto.fechaNac, dto.rol
        );
        usuarioRepository.save(usuario);
        return ResponseEntity.ok("Registro exitoso");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDTO dto) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByEmail(dto.email);
        if (usuarioOpt.isEmpty()) return ResponseEntity.status(401).body("Email no encontrado");

        Usuario usuario = usuarioOpt.get();
        if (!usuario.getContrasenia().equals(dto.contrasenia)) {
            return ResponseEntity.status(401).body("Contraseña incorrecta");
        }

        return ResponseEntity.ok("Inicio de sesión exitoso");
    }
}
