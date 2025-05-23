// src/main/java/com/omnibus/backend/controller/AdminController.java
package com.omnibus.backend.controller;

import com.omnibus.backend.dto.CreatePrivilegedUserDTO;
import com.omnibus.backend.model.Administrador;
import com.omnibus.backend.model.Usuario; // Clase base
import com.omnibus.backend.model.Vendedor;
import com.omnibus.backend.repository.UsuarioRepository;
import jakarta.validation.Valid; // Para activar la validación del DTO
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users") // Un prefijo común para operaciones de admin sobre usuarios
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @PostMapping("/create-privileged")
    @PreAuthorize("hasRole('ADMINISTRADOR')") // Solo administradores pueden acceder
    public ResponseEntity<?> createPrivilegedUser(@Valid @RequestBody CreatePrivilegedUserDTO dto) {
        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El email ya está registrado."));
        }

        Usuario nuevoUsuario;

        if ("ADMINISTRADOR".equalsIgnoreCase(dto.tipoRolACrear)) {
            nuevoUsuario = new Administrador(
                    dto.nombre, dto.apellido, dto.ci,
                    passwordEncoder.encode(dto.contrasenia),
                    dto.email, dto.telefono, dto.fechaNac,
                    dto.areaResponsabilidad != null ? dto.areaResponsabilidad : "General" // Ejemplo
            );
        } else if ("VENDEDOR".equalsIgnoreCase(dto.tipoRolACrear)) {
            if (dto.codigoVendedor == null || dto.codigoVendedor.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El código de vendedor es obligatorio para crear un Vendedor."));
            }
            // Podrías verificar si el codigoVendedor ya existe
            nuevoUsuario = new Vendedor(
                    dto.nombre, dto.apellido, dto.ci,
                    passwordEncoder.encode(dto.contrasenia),
                    dto.email, dto.telefono, dto.fechaNac,
                    dto.codigoVendedor
            );
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Tipo de rol a crear inválido. Debe ser ADMINISTRADOR o VENDEDOR."));
        }

        try {
            usuarioRepository.save(nuevoUsuario);
            String tipoCreado = dto.tipoRolACrear.substring(0, 1).toUpperCase() + dto.tipoRolACrear.substring(1).toLowerCase();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Usuario " + tipoCreado + " creado exitosamente: " + nuevoUsuario.getEmail()));
        } catch (Exception e) {
            logger.error("Error al crear usuario privilegiado ({}): {}", dto.tipoRolACrear, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno al crear el usuario."));
        }
    }

    // Manejador de excepciones para errores de validación del DTO
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Map<String, String> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        errors.put("messageGeneral", "Error de validación en los datos enviados.");
        return errors;
    }

    // Podrías añadir más endpoints aquí:
    // - Listar usuarios (con paginación y filtros)
    // - Ver detalle de un usuario
    // - Modificar rol de un usuario (con mucho cuidado y lógica de negocio clara)
    // - Desactivar/Activar usuarios
}