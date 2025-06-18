package com.omnibus.backend.service;

import com.omnibus.backend.dto.UpdateUserDTO;
import com.omnibus.backend.dto.UserProfileDTO;
import com.omnibus.backend.dto.UsuarioStatsDTO;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService userService;

    private Usuario mockUsuario;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockUsuario = new Cliente();
        mockUsuario.setId(1L);
        mockUsuario.setEmail("test@example.com");
        mockUsuario.setContrasenia("hashedPassword");
        mockUsuario.setFechaCreacion(LocalDateTime.now());
        mockUsuario.setFechaNac(LocalDate.of(1990, 1, 1));
    }

    @Test
    void getUserProfileByEmail_success() {
        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        UserProfileDTO dto = userService.getUserProfileByEmail("test@example.com");
        assertEquals("test@example.com", dto.getEmail());
    }

    @Test
    void getUserProfileByEmail_notFound() {
        when(usuarioRepository.findByEmail("notfound@example.com")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class, () -> userService.getUserProfileByEmail("notfound@example.com"));
    }

    @Test
    void updateUserProfile_success() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setNombre("Nuevo");
        dto.setApellido("Apellido");
        dto.setCi("12345678");
        dto.setTelefono("123456789");
        dto.setFechaNac("1990-01-01");

        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        when(usuarioRepository.save(any())).thenReturn(mockUsuario);

        UserProfileDTO result = userService.updateUserProfile("test@example.com", dto);
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void updateUserProfile_invalidCi_throwsException() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setCi("invalid");

        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        assertThrows(IllegalArgumentException.class, () -> userService.updateUserProfile("test@example.com", dto));
    }

    @Test
    void updateUserProfile_invalidTelefono_throwsException() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setTelefono("noNum");

        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        assertThrows(IllegalArgumentException.class, () -> userService.updateUserProfile("test@example.com", dto));
    }

    @Test
    void updateUserProfile_invalidFechaNac_throwsException() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setFechaNac("13-2020-01");

        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        assertThrows(IllegalArgumentException.class, () -> userService.updateUserProfile("test@example.com", dto));
    }

    @Test
    void updateUserProfile_emailExistente_throwsException() {
        UpdateUserDTO dto = new UpdateUserDTO();
        dto.setEmail("nuevo@example.com");

        Usuario otro = new Cliente();
        otro.setId(2L);
        otro.setEmail("nuevo@example.com");

        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        when(usuarioRepository.findByEmail("nuevo@example.com")).thenReturn(Optional.of(otro));

        assertThrows(IllegalArgumentException.class, () -> userService.updateUserProfile("test@example.com", dto));
    }

    @Test
    void changePassword_success() {
        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        when(passwordEncoder.matches("currentPass", "hashedPassword")).thenReturn(true);
        when(passwordEncoder.matches("newPass", "hashedPassword")).thenReturn(false);
        when(passwordEncoder.encode("newPass")).thenReturn("encodedNewPass");

        userService.changePassword("test@example.com", "currentPass", "newPass");
        verify(usuarioRepository).save(any());
    }

    @Test
    void changePassword_wrongCurrentPassword() {
        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        when(passwordEncoder.matches("wrongPass", "hashedPassword")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                userService.changePassword("test@example.com", "wrongPass", "newPass"));
    }

    @Test
    void changePassword_sameAsCurrentPassword() {
        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));
        when(passwordEncoder.matches("currentPass", "hashedPassword")).thenReturn(true);
        when(passwordEncoder.matches("currentPass", "hashedPassword")).thenReturn(true); // misma clave

        assertThrows(IllegalArgumentException.class, () ->
                userService.changePassword("test@example.com", "currentPass", "currentPass"));
    }

    @Test
    void requestPasswordReset_success() {
        when(usuarioRepository.findByEmail("test@example.com")).thenReturn(Optional.of(mockUsuario));

        userService.requestPasswordReset("test@example.com");
        verify(usuarioRepository).save(mockUsuario);
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), any());
    }

    @Test
    void requestPasswordReset_emailNoExiste() {
        when(usuarioRepository.findByEmail("desconocido@example.com")).thenReturn(Optional.empty());
        userService.requestPasswordReset("desconocido@example.com");
        verify(usuarioRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void resetPassword_tokenExpired() {
        mockUsuario.setResetPasswordToken("token123");
        mockUsuario.setResetPasswordTokenExpiryDate(LocalDateTime.now().minusMinutes(1));

        when(usuarioRepository.findByResetPasswordToken("token123")).thenReturn(Optional.of(mockUsuario));

        boolean result = userService.resetPassword("token123", "newPassword");
        assertFalse(result);
        verify(usuarioRepository).save(mockUsuario);
    }

    @Test
    void resetPassword_success() {
        mockUsuario.setResetPasswordToken("token123");
        mockUsuario.setResetPasswordTokenExpiryDate(LocalDateTime.now().plusMinutes(10));

        when(usuarioRepository.findByResetPasswordToken("token123")).thenReturn(Optional.of(mockUsuario));
        when(passwordEncoder.encode("newPassword")).thenReturn("encodedPassword");

        boolean result = userService.resetPassword("token123", "newPassword");
        assertTrue(result);
        verify(usuarioRepository).save(mockUsuario);
    }

    @Test
    void resetPassword_tokenNoExiste() {
        when(usuarioRepository.findByResetPasswordToken("invalido")).thenReturn(Optional.empty());
        boolean result = userService.resetPassword("invalido", "pass");
        assertFalse(result);
    }

    @Test
    void deleteUserById_success() {
        when(usuarioRepository.existsById(1L)).thenReturn(true);
        userService.deleteUserById(1L);
        verify(usuarioRepository).deleteById(1L);
    }

    @Test
    void deleteUserById_notFound() {
        when(usuarioRepository.existsById(99L)).thenReturn(false);
        assertThrows(UsernameNotFoundException.class, () -> userService.deleteUserById(99L));
    }

    @Test
    void obtenerDatosParaEstadisticas_success() {
        Cliente cliente = new Cliente();
        cliente.setFechaCreacion(LocalDateTime.now());
        cliente.setFechaNac(LocalDate.of(1990, 1, 1));
        cliente.setTipo(TipoCliente.COMUN);

        Administrador admin = new Administrador();
        admin.setFechaCreacion(LocalDateTime.now());
        admin.setFechaNac(LocalDate.of(1985, 5, 5));

        when(usuarioRepository.findAll()).thenReturn(List.of(cliente, admin));
        List<UsuarioStatsDTO> stats = userService.obtenerDatosParaEstadisticas();
        assertEquals(2, stats.size());
    }
}
