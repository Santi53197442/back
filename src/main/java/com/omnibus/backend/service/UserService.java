package com.omnibus.backend.service;

import com.omnibus.backend.dto.UpdateUserDTO;
import com.omnibus.backend.dto.UserProfileDTO;
import com.omnibus.backend.dto.UsuarioStatsDTO;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    public UserProfileDTO getUserProfileByEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));
        return new UserProfileDTO(usuario);
    }

    @Transactional
    public UserProfileDTO updateUserProfile(String currentEmail, UpdateUserDTO dto) {
        Usuario usuario = usuarioRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + currentEmail));

        // Actualizar campos si se proporcionan en el DTO
        if (dto.getNombre() != null && !dto.getNombre().trim().isEmpty()) {
            usuario.setNombre(dto.getNombre().trim());
        }
        if (dto.getApellido() != null && !dto.getApellido().trim().isEmpty()) {
            usuario.setApellido(dto.getApellido().trim());
        }
        if (dto.getCi() != null && !dto.getCi().trim().isEmpty()) {
            try {
                usuario.setCi(Integer.parseInt(dto.getCi().trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("CI inválido, debe ser numérico.");
            }
        }
        if (dto.getTelefono() != null && !dto.getTelefono().trim().isEmpty()) {
            try {
                usuario.setTelefono(Integer.parseInt(dto.getTelefono().trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Teléfono inválido, debe ser numérico.");
            }
        }
        if (dto.getFechaNac() != null && !dto.getFechaNac().trim().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE; // YYYY-MM-DD
                LocalDate fechaNac = LocalDate.parse(dto.getFechaNac(), formatter);
                usuario.setFechaNac(fechaNac);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Formato de fecha de nacimiento inválido. Use YYYY-MM-DD.");
            }
        }
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty() && !dto.getEmail().trim().equalsIgnoreCase(currentEmail)) {
            String newEmail = dto.getEmail().trim();
            Optional<Usuario> existingUserWithNewEmail = usuarioRepository.findByEmail(newEmail);
            if (existingUserWithNewEmail.isPresent() && !existingUserWithNewEmail.get().getId().equals(usuario.getId())) {
                throw new IllegalArgumentException("El nuevo email ya está registrado por otro usuario.");
            }
            usuario.setEmail(newEmail);
        }

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        return new UserProfileDTO(usuarioGuardado);
    }

    // --- MÉTODO PARA CAMBIAR CONTRASEÑA (USUARIO AUTENTICADO) ---
    /**
     * Permite a un usuario autenticado cambiar su propia contraseña.
     *
     * @param email El email del usuario autenticado.
     * @param currentPassword La contraseña actual proporcionada por el usuario.
     * @param newPassword La nueva contraseña deseada por el usuario.
     * @throws UsernameNotFoundException Si el usuario no se encuentra.
     * @throws IllegalArgumentException Si la contraseña actual es incorrecta o la nueva contraseña no es válida.
     */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));

        // 1. Verificar la contraseña actual
        // Usamos getPassword() porque tu clase Usuario lo implementa de UserDetails
        if (!passwordEncoder.matches(currentPassword, usuario.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        }

        // 2. Opcional: Verificar si la nueva contraseña es igual a la anterior
        // Usamos getPassword() aquí también
        if (passwordEncoder.matches(newPassword, usuario.getPassword())) {
            throw new IllegalArgumentException("La nueva contraseña no puede ser igual a la contraseña actual.");
        }

        // 3. Codificar y establecer la nueva contraseña
        usuario.setContrasenia(passwordEncoder.encode(newPassword)); // Usas setContrasenia, eso está bien
        usuarioRepository.save(usuario);

        // Opcional: podrías enviar un email de notificación de cambio de contraseña aquí.
        // emailService.sendPasswordChangedNotification(usuario.getEmail());
    }
    // --- FIN DEL MÉTODO ---


    // --- MÉTODOS PARA RECUPERACIÓN DE CONTRASEÑA (OLVIDÉ MI CONTRASEÑA) ---

    @Transactional
    public void requestPasswordReset(String email) {
        Optional<Usuario> usuarioOptional = usuarioRepository.findByEmail(email);

        if (usuarioOptional.isPresent()) {
            Usuario usuario = usuarioOptional.get();
            String token = UUID.randomUUID().toString();
            usuario.setResetPasswordToken(token);
            usuario.setResetPasswordTokenExpiryDate(LocalDateTime.now().plusHours(1)); // Token válido por 1 hora
            usuarioRepository.save(usuario);

            emailService.sendPasswordResetEmail(usuario.getEmail(), token);
            System.out.println("Solicitud de reseteo para: " + email + " Token: " + token);
        } else {
            System.out.println("Solicitud de reseteo para email no encontrado (o se maneja silenciosamente): " + email);
        }
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<Usuario> usuarioOptional = usuarioRepository.findByResetPasswordToken(token);

        if (usuarioOptional.isEmpty()) {
            System.out.println("Intento de reseteo con token inválido: " + token);
            return false;
        }

        Usuario usuario = usuarioOptional.get();

        if (usuario.getResetPasswordTokenExpiryDate().isBefore(LocalDateTime.now())) {
            usuario.setResetPasswordToken(null);
            usuario.setResetPasswordTokenExpiryDate(null);
            usuarioRepository.save(usuario);
            System.out.println("Intento de reseteo con token expirado: " + token);
            return false;
        }

        usuario.setContrasenia(passwordEncoder.encode(newPassword));
        usuario.setResetPasswordToken(null);
        usuario.setResetPasswordTokenExpiryDate(null);
        usuarioRepository.save(usuario);

        System.out.println("Contraseña reseteada exitosamente para usuario con token: " + token);
        return true;
    }

    @Transactional
    public void deleteUserById(Long userId) {
        // Primero, verifica si el usuario existe antes de intentar eliminarlo.
        // Esto permite lanzar una excepción más clara si no se encuentra.
        if (!usuarioRepository.existsById(userId)) {
            // Lanza una excepción personalizada o una estándar.
            // Esto es útil para devolver un 404 Not Found desde el controlador.
            throw new UsernameNotFoundException("Usuario no encontrado con ID: " + userId);
        }
        usuarioRepository.deleteById(userId);
    }


    public List<UsuarioStatsDTO> obtenerDatosParaEstadisticas() {
        List<Usuario> usuarios = usuarioRepository.findAll();

        return usuarios.stream().map(usuario -> {
            String rol = "DESCONOCIDO";
            TipoCliente tipoCliente = null;

            if (usuario instanceof Cliente) {
                rol = "CLIENTE";
                tipoCliente = ((Cliente) usuario).getTipo();
            } else if (usuario instanceof Vendedor) {
                rol = "VENDEDOR";
            } else if (usuario instanceof Administrador) {
                rol = "ADMINISTRADOR";
            }

            return new UsuarioStatsDTO(
                    rol,
                    usuario.getFechaCreacion(),
                    tipoCliente,
                    usuario.getFechaNac() // <-- AÑADIR ESTA LÍNEA
            );
        }).collect(Collectors.toList());
    }
}