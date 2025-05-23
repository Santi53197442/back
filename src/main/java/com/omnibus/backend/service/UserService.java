package com.omnibus.backend.service; // Asegúrate de que el paquete sea el correcto

import com.omnibus.backend.dto.UpdateUserDTO;
import com.omnibus.backend.dto.UserProfileDTO;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
// import org.springframework.security.crypto.password.PasswordEncoder; // No necesario si no actualizamos contraseña
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UsuarioRepository usuarioRepository;

    // Opcional: Si necesitas un PasswordEncoder para otras cosas en este servicio
    // @Autowired
    // private PasswordEncoder passwordEncoder;

    public UserProfileDTO getUserProfileByEmail(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado con email: " + email));
        return new UserProfileDTO(usuario); // Convierte la entidad a DTO
    }

    @Transactional // Importante para asegurar que la actualización sea atómica
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
                // Asume que tu entidad Usuario espera un Integer o Long para CI
                usuario.setCi(Integer.parseInt(dto.getCi().trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("CI inválido, debe ser numérico.");
            }
        }

        if (dto.getTelefono() != null && !dto.getTelefono().trim().isEmpty()) {
            try {
                // Asume que tu entidad Usuario espera un Integer o Long para Telefono
                usuario.setTelefono(Integer.parseInt(dto.getTelefono().trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Teléfono inválido, debe ser numérico.");
            }
        }

        if (dto.getFechaNac() != null && !dto.getFechaNac().trim().isEmpty()) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE; // YYYY-MM-DD
                LocalDate fechaNac = LocalDate.parse(dto.getFechaNac(), formatter);
                usuario.setFechaNac(fechaNac); // Asume que tu entidad Usuario tiene un campo LocalDate fechaNac
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Formato de fecha de nacimiento inválido. Use YYYY-MM-DD.");
            }
        }

        // Manejo del cambio de email (considerar implicaciones de seguridad y unicidad)
        if (dto.getEmail() != null && !dto.getEmail().trim().isEmpty() && !dto.getEmail().trim().equalsIgnoreCase(currentEmail)) {
            String newEmail = dto.getEmail().trim();
            // Verificar si el nuevo email ya está en uso por OTRO usuario
            Optional<Usuario> existingUserWithNewEmail = usuarioRepository.findByEmail(newEmail);
            if (existingUserWithNewEmail.isPresent() && !existingUserWithNewEmail.get().getId().equals(usuario.getId())) {
                throw new IllegalArgumentException("El nuevo email ya está registrado por otro usuario.");
            }
            usuario.setEmail(newEmail);
            // NOTA: Si el email es parte del token JWT o se usa para identificar al usuario en la sesión,
            // cambiarlo aquí podría requerir que el usuario vuelva a iniciar sesión o que se reemita el token.
            // Para este ejemplo, simplemente lo actualizamos. Considera las implicaciones.
        }

        Usuario usuarioGuardado = usuarioRepository.save(usuario);
        return new UserProfileDTO(usuarioGuardado); // Devuelve el DTO del usuario actualizado
    }
}