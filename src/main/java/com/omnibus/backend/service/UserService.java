package com.omnibus.backend.service;

import com.omnibus.backend.dto.UpdateUserDTO;
import com.omnibus.backend.dto.UserProfileDTO;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder; // <-- Necesario para hashear nueva contraseña
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime; // Para la expiración del token
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.UUID; // Para generar tokens únicos

@Service
public class UserService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // <-- Inyectar PasswordEncoder

    @Autowired
    private EmailService emailService; // <-- Inyectar EmailService (debes crear esta clase)

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

    // --- NUEVOS MÉTODOS PARA RECUPERACIÓN DE CONTRASEÑA ---

    /**
     * Inicia el proceso de restablecimiento de contraseña para un usuario.
     * Genera un token, lo guarda en el usuario y envía un correo electrónico.
     *
     * @param email El correo electrónico del usuario que solicita el restablecimiento.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        Optional<Usuario> usuarioOptional = usuarioRepository.findByEmail(email);

        if (usuarioOptional.isPresent()) {
            Usuario usuario = usuarioOptional.get();
            String token = UUID.randomUUID().toString();
            usuario.setResetPasswordToken(token);
            usuario.setResetPasswordTokenExpiryDate(LocalDateTime.now().plusHours(1)); // Token válido por 1 hora
            usuarioRepository.save(usuario);

            // Enviar correo electrónico
            emailService.sendPasswordResetEmail(usuario.getEmail(), token);
            System.out.println("Solicitud de reseteo para: " + email + " Token: " + token); // Para debug
        } else {
            // No lanzar error para no revelar si un email existe o no (seguridad).
            // El controlador debe devolver un mensaje genérico.
            System.out.println("Solicitud de reseteo para email no encontrado (o se maneja silenciosamente): " + email);
        }
    }

    /**
     * Restablece la contraseña del usuario si el token es válido y no ha expirado.
     *
     * @param token El token de restablecimiento.
     * @param newPassword La nueva contraseña (en texto plano, se hasheará aquí).
     * @return true si la contraseña se restableció con éxito, false en caso contrario.
     */
    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        Optional<Usuario> usuarioOptional = usuarioRepository.findByResetPasswordToken(token);

        if (usuarioOptional.isEmpty()) {
            System.out.println("Intento de reseteo con token inválido: " + token);
            return false; // Token no encontrado
        }

        Usuario usuario = usuarioOptional.get();

        if (usuario.getResetPasswordTokenExpiryDate().isBefore(LocalDateTime.now())) {
            // Token expirado, limpiarlo para que no se pueda reusar.
            usuario.setResetPasswordToken(null);
            usuario.setResetPasswordTokenExpiryDate(null);
            usuarioRepository.save(usuario);
            System.out.println("Intento de reseteo con token expirado: " + token);
            return false; // Token expirado
        }

        // Token válido y no expirado
        usuario.setContrasenia(passwordEncoder.encode(newPassword)); // Hashear la nueva contraseña
        usuario.setResetPasswordToken(null); // Invalidar el token después de su uso
        usuario.setResetPasswordTokenExpiryDate(null);
        usuarioRepository.save(usuario);

        System.out.println("Contraseña reseteada exitosamente para usuario con token: " + token);
        return true;
    }

    // --- FIN DE NUEVOS MÉTODOS ---
}