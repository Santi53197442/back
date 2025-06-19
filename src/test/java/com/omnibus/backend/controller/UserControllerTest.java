// UserControllerTest.java
package com.omnibus.backend.controller;

import com.omnibus.backend.dto.ChangePasswordDTO;
import com.omnibus.backend.dto.UpdateUserDTO;
import com.omnibus.backend.dto.UserProfileDTO;
import com.omnibus.backend.dto.ClienteEncontradoDTO;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UserControllerTest {

    @Mock private UserService userService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private Authentication authentication;
    @Mock private SecurityContext securityContext;

    @InjectMocks private UserController userController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testGetCurrentUserProfile_success() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("user@mail.com");

        UserProfileDTO profileDTO = new UserProfileDTO();
        when(userService.getUserProfileByEmail("user@mail.com")).thenReturn(profileDTO);

        ResponseEntity<?> response = userController.getCurrentUserProfile();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(profileDTO, response.getBody());
    }

    @Test
    void testUpdateUserProfile_success() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("user@mail.com");

        UpdateUserDTO dto = new UpdateUserDTO();
        UserProfileDTO updated = new UserProfileDTO();
        when(userService.updateUserProfile("user@mail.com", dto)).thenReturn(updated);

        ResponseEntity<?> response = userController.updateUserProfile(dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(updated, response.getBody());
    }

    @Test
    void testChangePassword_success() {
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("user@mail.com");

        ChangePasswordDTO dto = new ChangePasswordDTO("old", "new");

        ResponseEntity<?> response = userController.changePassword(dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("Contraseña actualizada"));
    }

    @Test
    void testBuscarClientePorCi_clienteEncontrado() {
        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNombre("Juan");
        cliente.setApellido("Perez");
        cliente.setEmail("juan@mail.com");
        cliente.setCi(12345678);

        when(usuarioRepository.findByCi(12345678)).thenReturn(Optional.of(cliente));

        ResponseEntity<?> response = userController.buscarClientePorCi("12345678");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof ClienteEncontradoDTO);
    }

    @Test
    void testBuscarClientePorCi_usuarioNoEsCliente() {
        Usuario usuario = mock(Usuario.class);
        when(usuarioRepository.findByCi(12345678)).thenReturn(Optional.of(usuario));

        ResponseEntity<?> response = userController.buscarClientePorCi("12345678");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testBuscarClientePorCi_noEncontrado() {
        when(usuarioRepository.findByCi(12345678)).thenReturn(Optional.empty());

        ResponseEntity<?> response = userController.buscarClientePorCi("12345678");
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testBuscarClientePorCi_ciInvalida() {
        ResponseEntity<?> response = userController.buscarClientePorCi("abc123");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testBuscarClientePorCi_ciVacia() {
        ResponseEntity<?> response = userController.buscarClientePorCi(" ");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
