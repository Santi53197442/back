// AuthControllerTest.java
package com.omnibus.backend.controller;

import com.omnibus.backend.dto.AuthResponseDTO;
import com.omnibus.backend.dto.LoginDTO;
import com.omnibus.backend.dto.RegisterDTO;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.TipoCliente;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.security.JwtUtil;
import com.omnibus.backend.service.CustomUserDetailsService;
import com.omnibus.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AuthControllerTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private CustomUserDetailsService userDetailsService;
    @Mock private JwtUtil jwtUtil;
    @Mock private UserService userService;

    @InjectMocks private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testRegister_success() {
        RegisterDTO dto = new RegisterDTO("Juan", "Perez", 12345678, "pass", "juan@mail.com", 987654321, LocalDate.now(),"rol");
        when(usuarioRepository.findByEmail(dto.email)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(dto.contrasenia)).thenReturn("hashed");

        ResponseEntity<?> response = authController.register(dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(((Map<?, ?>) response.getBody()).get("message").toString().contains("registrado"));
    }

    @Test
    void testRegister_emailYaRegistrado() {
        RegisterDTO dto = new RegisterDTO("Juan", "Perez", 12345678, "pass", "juan@mail.com", 987654321, LocalDate.now(),"rol");
        when(usuarioRepository.findByEmail(dto.email)).thenReturn(Optional.of(new Cliente()));

        ResponseEntity<?> response = authController.register(dto);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testLogin_success() {
        LoginDTO dto = new LoginDTO("juan@mail.com", "pass");
        Cliente cliente = new Cliente();
        cliente.setId(1L);
        cliente.setNombre("Juan");
        cliente.setApellido("Perez");
        cliente.setEmail(dto.email);
        cliente.setTipo(TipoCliente.COMUN);

        when(userDetailsService.loadUserByUsername(dto.email)).thenReturn(cliente);
        when(jwtUtil.generateToken(cliente)).thenReturn("fake-jwt");

        ResponseEntity<?> response = authController.login(dto);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof AuthResponseDTO);
    }

    @Test
    void testLogin_badCredentials() {
        LoginDTO dto = new LoginDTO("juan@mail.com", "wrong");
        doThrow(new BadCredentialsException("Bad credentials")).when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        ResponseEntity<?> response = authController.login(dto);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void testForgotPassword_success() {
        AuthController.EmailRequest req = new AuthController.EmailRequest();
        req.email = "juan@mail.com";

        ResponseEntity<?> response = authController.forgotPassword(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testForgotPassword_emailVacio() {
        AuthController.EmailRequest req = new AuthController.EmailRequest();
        req.email = " ";

        ResponseEntity<?> response = authController.forgotPassword(req);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testResetPassword_success() {
        AuthController.ResetPasswordRequest req = new AuthController.ResetPasswordRequest();
        req.token = "valid-token";
        req.newPassword = "newpass";

        when(userService.resetPassword(req.token, req.newPassword)).thenReturn(true);

        ResponseEntity<?> response = authController.resetPassword(req);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testResetPassword_camposFaltantes() {
        AuthController.ResetPasswordRequest req = new AuthController.ResetPasswordRequest();
        req.token = null;
        req.newPassword = " ";

        ResponseEntity<?> response = authController.resetPassword(req);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testResetPassword_tokenInvalido() {
        AuthController.ResetPasswordRequest req = new AuthController.ResetPasswordRequest();
        req.token = "invalid";
        req.newPassword = "123456";

        when(userService.resetPassword(req.token, req.newPassword)).thenReturn(false);

        ResponseEntity<?> response = authController.resetPassword(req);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
