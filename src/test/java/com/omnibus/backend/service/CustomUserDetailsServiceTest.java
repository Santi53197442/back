package com.omnibus.backend.service;

import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.UserDetails;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    void loadUserByUsername_whenUserExists_returnsUserDetails() {
        // Preparamos un mock de usuario que implementa UserDetails
        Usuario usuarioMock = mock(Usuario.class);
        when(usuarioRepository.findByEmail("test@mail.com"))
                .thenReturn(Optional.of(usuarioMock));

        // Ejecutamos el métoddo
        UserDetails result = service.loadUserByUsername("test@mail.com");

        // Verificamos que devuelve exactamente el objeto que el repo retornó
        assertSame(usuarioMock, result);
        verify(usuarioRepository, times(1)).findByEmail("test@mail.com");
    }

    @Test
    void loadUserByUsername_whenUserNotFound_throwsException() {
        when(usuarioRepository.findByEmail("no@mail.com"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException ex = assertThrows(
                UsernameNotFoundException.class,
                () -> service.loadUserByUsername("no@mail.com")
        );

        // El mensaje debe incluir el email
        assertTrue(ex.getMessage().contains("Usuario no encontrado con email: no@mail.com"));
        verify(usuarioRepository, times(1)).findByEmail("no@mail.com");
    }
}
