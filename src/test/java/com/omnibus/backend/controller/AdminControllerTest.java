package com.omnibus.backend.controller;
import jakarta.validation.Validator;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.charset.StandardCharsets;
import com.omnibus.backend.dto.CreatePrivilegedUserDTO;
import com.omnibus.backend.dto.PaginatedUserResponseDTO;
import com.omnibus.backend.dto.UsuarioStatsDTO;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminControllerTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private Validator validator;
    @Mock private MultipartFile multipartFile;

    @InjectMocks
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreatePrivilegedUser_success() {
        // Preparar DTO
        CreatePrivilegedUserDTO dto = new CreatePrivilegedUserDTO();
        dto.nombre = "Juan";
        dto.apellido = "Pérez";
        dto.ci = "12345678";
        dto.contrasenia = "secreto";
        dto.email = "juan@mail.com";
        dto.telefono = "87654321";
        dto.tipoRolACrear = "ADMINISTRADOR";

        // Mocks
        when(usuarioRepository.findByEmail("juan@mail.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("secreto"))
                .thenReturn("encoded");
        // Guardar devuelve la misma instancia
        when(usuarioRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

        // Ejecutar
        ResponseEntity<?> response = adminController.createPrivilegedUser(dto);

        // Verificar
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String,String> body = (Map<String,String>) response.getBody();
        assertNotNull(body);
        assertTrue(body.get("message")
                .contains("Usuario Administrador creado exitosamente: juan@mail.com"));
    }

    @Test
    void testCreatePrivilegedUser_emailExists() {
        CreatePrivilegedUserDTO dto = new CreatePrivilegedUserDTO();
        dto.email = "existe@mail.com";
        when(usuarioRepository.findByEmail("existe@mail.com"))
                .thenReturn(Optional.of(mock(Usuario.class)));

        ResponseEntity<?> response = adminController.createPrivilegedUser(dto);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String,String> body = (Map<String,String>) response.getBody();
        assertEquals("El email 'existe@mail.com' ya está registrado.", body.get("message"));
    }

    @Test
    void testCreatePrivilegedUserBatch_emptyFile() {
        when(multipartFile.isEmpty()).thenReturn(true);

        ResponseEntity<?> response = adminController.createPrivilegedUserBatch(multipartFile);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String,String> body = (Map<String,String>) response.getBody();
        assertEquals("El archivo CSV no puede estar vacío.", body.get("message"));
    }

    @Test
    void testHandleValidationExceptions() {
        // Mockear excepción y binding result
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult br = mock(BindingResult.class);
        when(ex.getBindingResult()).thenReturn(br);

        FieldError fe = new FieldError("obj","campo","Mensaje inválido");
        when(br.getAllErrors()).thenReturn(List.of(fe));

        Map<String,String> errors = adminController.handleValidationExceptions(ex);

        assertEquals("Mensaje inválido", errors.get("campo"));
        assertEquals("Error de validación en los datos enviados.", errors.get("messageGeneral"));
    }

    @Test
    void testGetAllUsers_success() {
        Pageable pageable = PageRequest.of(0, 5);
        // Usamos una instancia real de Cliente para que instanceof funcione
        Usuario usuario = new Cliente();
        Page<Usuario> page = new PageImpl<>(List.of(usuario), pageable, 1);
        when(usuarioRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(page);

        ResponseEntity<PaginatedUserResponseDTO> response =
                adminController.getAllUsers(pageable, null, null, null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        PaginatedUserResponseDTO dto = response.getBody();
        assertNotNull(dto);
        assertEquals(1, dto.getContent().size());
        assertEquals(1L, dto.getTotalItems());
        assertEquals(1, dto.getTotalPages());
        assertEquals(0, dto.getCurrentPage());
    }

    @Test
    void testDeleteUser_success() {
        // No lanza excepción
        doNothing().when(userService).deleteUserById(1L);

        ResponseEntity<Void> response = adminController.deleteUser(1L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void testDeleteUser_notFound() {
        doThrow(new UsernameNotFoundException("no existe"))
                .when(userService).deleteUserById(42L);

        ResponseEntity<Void> response = adminController.deleteUser(42L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void testObtenerEstadisticasDeUsuarios_success() {
        List<UsuarioStatsDTO> stats = List.of(mock(UsuarioStatsDTO.class));
        when(userService.obtenerDatosParaEstadisticas()).thenReturn(stats);

        ResponseEntity<?> response = adminController.obtenerEstadisticasDeUsuarios();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(stats, response.getBody());
    }

    @Test
    void testObtenerEstadisticasDeUsuarios_error() {
        when(userService.obtenerDatosParaEstadisticas())
                .thenThrow(new RuntimeException("boom"));

        ResponseEntity<?> response = adminController.obtenerEstadisticasDeUsuarios();

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String,String> body = (Map<String,String>) response.getBody();
        assertTrue(body.get("message")
                .contains("Error interno al procesar la solicitud de estadísticas"));
    }

    @Test
    void testCreatePrivilegedUserBatch_mixedRecords() throws Exception {
        // 1) Creamos un CSV con 2 filas:
        //    - la primera válida para ADMINISTRADOR
        //    - la segunda con CI no numérico (debe fallar antes de llegar a processPrivilegedUserCreation)
        String csv = """
        nombre,apellido,ci,contrasenia,email,telefono,fechaNac,tipoRolACrear,areaResponsabilidad
        Juan,Perez,12345,pass,juan@mail.com,67890,2025-06-18,ADMINISTRADOR,Area1
        Ana,Gomez,abcde,pass,ana@mail.com,12345,2025-06-18,ADMINISTRADOR,Area2
        """;

        // 2) Mockeamos el MultipartFile para que devuelva ese contenido
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "users.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        // 3) Para la fila válida: email no existe → paso a creación
        when(usuarioRepository.findByEmail("juan@mail.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("pass")).thenReturn("encodedPass");
        when(usuarioRepository.save(any(Usuario.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        // 4) Ejecutamos el método
        @SuppressWarnings("unchecked")
        ResponseEntity<?> response = adminController.createPrivilegedUserBatch(file);

        // 5) Verificamos HTTP 200 y el cuerpo con estadísticas
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String,Object> body = (Map<String,Object>) response.getBody();
        assertNotNull(body);

        // totalProcessed debe ser 2 filas
        assertEquals(2, body.get("totalProcessed"));
        // successfulCreations = 1 (Juan)
        assertEquals(1, body.get("successfulCreations"));
        // failedCreations = 1 (Ana con CI inválido)
        assertEquals(1, body.get("failedCreations"));

        @SuppressWarnings("unchecked")
        List<String> successDetails = (List<String>) body.get("successDetails");
        assertEquals(1, successDetails.size());
        assertTrue(successDetails.get(0).contains("Fila 1"));

        @SuppressWarnings("unchecked")
        List<Map<String,String>> failureDetails = (List<Map<String,String>>) body.get("failureDetails");
        assertEquals(1, failureDetails.size());
        Map<String,String> err = failureDetails.get(0);
        assertEquals("2", err.get("row"));                       // fila 2
        assertEquals("ana@mail.com", err.get("email"));
        assertTrue(err.get("error").contains("El CI 'abcde' no es un número válido."));
    }

}