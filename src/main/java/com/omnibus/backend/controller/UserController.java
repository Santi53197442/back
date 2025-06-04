package com.omnibus.backend.controller;

import com.omnibus.backend.dto.ChangePasswordDTO;
import com.omnibus.backend.dto.UpdateUserDTO;
import com.omnibus.backend.dto.UserProfileDTO;
import com.omnibus.backend.dto.ClienteEncontradoDTO; // Importar el nuevo DTO
import com.omnibus.backend.model.Cliente;           // Importar la entidad Cliente
import com.omnibus.backend.model.Usuario;
// RoleType y GrantedAuthority no son estrictamente necesarios aquí si usamos 'instanceof Cliente'
// y la lógica de roles está encapsulada en las entidades para Spring Security.
import com.omnibus.backend.service.UserService;
import com.omnibus.backend.repository.UsuarioRepository;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final UsuarioRepository usuarioRepository; // Inyectado para usar findByCi

    @Autowired
    public UserController(UserService userService, UsuarioRepository usuarioRepository) {
        this.userService = userService;
        this.usuarioRepository = usuarioRepository;
    }

    // --- Tus métodos existentes (/profile, /password) ---
    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName();
        try {
            UserProfileDTO userProfile = userService.getUserProfileByEmail(currentUsername);
            return ResponseEntity.ok(userProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error al obtener el perfil del usuario {}: {}", currentUsername, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al obtener el perfil del usuario.");
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@Valid @RequestBody UpdateUserDTO updateUserDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName();
        try {
            UserProfileDTO updatedUserProfile = userService.updateUserProfile(currentUsername, updateUserDTO);
            return ResponseEntity.ok(updatedUserProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error interno al actualizar el perfil para {}: {}", currentUsername, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno al actualizar el perfil.");
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName();
        try {
            userService.changePassword(currentUsername, changePasswordDTO.getCurrentPassword(), changePasswordDTO.getNewPassword());
            return ResponseEntity.ok().body("Contraseña actualizada exitosamente.");
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error al cambiar la contraseña para {}: {}", currentUsername, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al cambiar la contraseña.");
        }
    }

    // --- NUEVO ENDPOINT PARA BUSCAR CLIENTE POR CI ---
    @GetMapping("/ci/{ci}")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> buscarClientePorCi(@PathVariable String ci) {
        if (ci == null || ci.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("La Cédula de Identidad (CI) no puede estar vacía.");
        }
        String ciTrimmed = ci.trim();

        Integer ciInteger;
        try {
            // Intentar parsear la CI a Integer, ya que ese es el tipo en la entidad Usuario
            // y en el método del repositorio findByCi(Integer ci)
            ciInteger = Integer.parseInt(ciTrimmed);
        } catch (NumberFormatException e) {
            logger.warn("CI proporcionada no es un número válido: {}", ciTrimmed);
            return ResponseEntity.badRequest().body("La CI proporcionada no tiene un formato numérico válido.");
        }

        logger.info("Petición para buscar cliente con CI (Integer): {}", ciInteger);
        Optional<Usuario> usuarioOpt = usuarioRepository.findByCi(ciInteger); // Llamar con Integer

        if (usuarioOpt.isPresent()) {
            Usuario usuario = usuarioOpt.get();

            // Usar instanceof para verificar si el Usuario es una instancia de Cliente
            if (usuario instanceof Cliente) {
                ClienteEncontradoDTO dto = new ClienteEncontradoDTO(
                        usuario.getId(),
                        usuario.getNombre(),
                        usuario.getApellido(),
                        String.valueOf(usuario.getCi()), // Convertir el Integer CI a String para el DTO
                        usuario.getEmail()
                );
                logger.info("Cliente encontrado con CI {}: ID {}, Nombre: {} {}", ciTrimmed, usuario.getId(), usuario.getNombre(), usuario.getApellido());
                return ResponseEntity.ok(dto);
            } else {
                String tipoUsuarioDetectado = usuario.getClass().getSimpleName();
                logger.warn("Usuario encontrado con CI {} pero no es un Cliente. Tipo de instancia: {}", ciTrimmed, tipoUsuarioDetectado);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Se encontró un usuario con CI " + ciTrimmed + ", pero no está registrado como cliente (tipo: " + tipoUsuarioDetectado + ").");
            }
        } else {
            logger.info("No se encontró ningún usuario con CI: {}", ciTrimmed);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("No se encontró cliente con CI: " + ciTrimmed + ".");
        }
    }
}