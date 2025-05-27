package com.omnibus.backend.controller;

import com.omnibus.backend.dto.ChangePasswordDTO;
import com.omnibus.backend.dto.UpdateUserDTO;
import com.omnibus.backend.dto.UserProfileDTO;
import com.omnibus.backend.service.UserService; // Asegúrate de que esta importación sea correcta
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user") // Ruta base para este controlador
@CrossOrigin(origins = "*")  // Ajusta en producción
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<?> getCurrentUserProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName(); // Email del usuario logueado

        try {
            UserProfileDTO userProfile = userService.getUserProfileByEmail(currentUsername);
            return ResponseEntity.ok(userProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(404).body("Usuario no encontrado: " + e.getMessage());
        } catch (Exception e) {
            // Log el error: e.printStackTrace(); o usa un logger
            return ResponseEntity.status(500).body("Error al obtener el perfil del usuario.");
        }
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateUserProfile(@RequestBody UpdateUserDTO updateUserDTO) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(401).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName(); // Email del usuario logueado

        try {
            UserProfileDTO updatedUserProfile = userService.updateUserProfile(currentUsername, updateUserDTO);
            return ResponseEntity.ok(updatedUserProfile);
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(404).body("Usuario no encontrado: " + e.getMessage());
        } catch (IllegalArgumentException e) { // Para errores de validación, ej. email ya existe
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            // Log el error: e.printStackTrace(); o usa un logger
            return ResponseEntity.status(500).body("Error interno al actualizar el perfil: " + e.getMessage());
        }
    }

    @PutMapping("/password") // Nuevo endpoint para cambiar contraseña
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordDTO changePasswordDTO) { // Añadido @Valid
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuario no autenticado.");
        }
        String currentUsername = authentication.getName(); // Email del usuario logueado

        try {
            userService.changePassword(currentUsername, changePasswordDTO.getCurrentPassword(), changePasswordDTO.getNewPassword());
            return ResponseEntity.ok().body("Contraseña actualizada exitosamente.");
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado: " + e.getMessage());
        } catch (IllegalArgumentException e) { // Captura errores como contraseña actual incorrecta o nueva contraseña inválida
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            // Log el error: e.printStackTrace(); o usa un logger
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al cambiar la contraseña: " + e.getMessage());
        }
    }
}