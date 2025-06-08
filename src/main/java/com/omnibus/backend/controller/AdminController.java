package com.omnibus.backend.controller;

import com.omnibus.backend.dto.CreatePrivilegedUserDTO;
import com.omnibus.backend.dto.UserViewDTO;
// --- IMPORTACIONES PARA ESTADÍSTICAS ---
import com.omnibus.backend.dto.dashboard.DashboardStatisticsDTO;
import com.omnibus.backend.model.Administrador;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.model.Vendedor;
import com.omnibus.backend.repository.UsuarioRepository;
// --- IMPORTACIONES PARA ESTADÍSTICAS ---
import com.omnibus.backend.service.DashboardService;
import com.omnibus.backend.service.UserService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Validator validator;

    @Autowired
    private UserService userService;

    // --- INYECTAR EL NUEVO SERVICIO ---
    @Autowired
    private DashboardService dashboardService;

    @PostMapping("/create-privileged")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> createPrivilegedUser(@Valid @RequestBody CreatePrivilegedUserDTO dto) {
        return processPrivilegedUserCreation(dto, -1);
    }

    @PostMapping("/create-privileged-batch")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<?> createPrivilegedUserBatch(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El archivo CSV no puede estar vacío."));
        }

        List<String> successMessages = new ArrayList<>();
        List<Map<String, String>> errorMessages = new ArrayList<>();
        int rowNum = 0;

        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            Iterable<CSVRecord> csvRecords = csvParser.getRecords();

            for (CSVRecord csvRecord : csvRecords) {
                rowNum++;
                CreatePrivilegedUserDTO dto = new CreatePrivilegedUserDTO();
                try {
                    dto.nombre = csvRecord.get("nombre");
                    dto.apellido = csvRecord.get("apellido");
                    dto.ci = csvRecord.get("ci");
                    dto.contrasenia = csvRecord.get("contrasenia");
                    dto.email = csvRecord.get("email");
                    dto.telefono = csvRecord.get("telefono");
                    try {
                        String fechaNacStr = csvRecord.get("fechaNac");
                        if (fechaNacStr != null && !fechaNacStr.trim().isEmpty()) {
                            dto.fechaNac = LocalDate.parse(fechaNacStr);
                        }
                    } catch (DateTimeParseException e) {
                        addError(errorMessages, rowNum, dto.email, "Formato de fechaNac inválido. Usar YYYY-MM-DD.");
                        continue;
                    }
                    dto.tipoRolACrear = csvRecord.get("tipoRolACrear");

                    if ("ADMINISTRADOR".equalsIgnoreCase(dto.tipoRolACrear)) {
                        dto.areaResponsabilidad = csvRecord.isSet("areaResponsabilidad") ? csvRecord.get("areaResponsabilidad") : null;
                    } else if ("VENDEDOR".equalsIgnoreCase(dto.tipoRolACrear)) {
                        dto.codigoVendedor = csvRecord.isSet("codigoVendedor") ? csvRecord.get("codigoVendedor") : null;
                    }

                    Set<ConstraintViolation<CreatePrivilegedUserDTO>> violations = validator.validate(dto);
                    if (!violations.isEmpty()) {
                        String errorDetails = violations.stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.joining(", "));
                        addError(errorMessages, rowNum, dto.email, "Error de validación: " + errorDetails);
                        continue;
                    }

                    ResponseEntity<?> response = processPrivilegedUserCreation(dto, rowNum);

                    if (response.getStatusCode().is2xxSuccessful()) {
                        Map<String, String> successBody = (Map<String, String>) response.getBody();
                        successMessages.add("Fila " + rowNum + ": " + (successBody != null ? successBody.get("message") : "Éxito"));
                    } else {
                        Map<String, String> errorBody = (Map<String, String>) response.getBody();
                        addError(errorMessages, rowNum, dto.email, (errorBody != null ? errorBody.get("message") : "Error desconocido"));
                    }

                } catch (IllegalArgumentException e) {
                    addError(errorMessages, rowNum, "N/A", "Error en la fila: " + e.getMessage() + ". Columnas requeridas podrían faltar.");
                } catch (Exception e) {
                    logger.error("Error procesando fila {} del CSV: {}", rowNum, e.getMessage(), e);
                    addError(errorMessages, rowNum, "N/A", "Error inesperado procesando la fila: " + e.getMessage());
                }
            }

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("totalProcessed", rowNum);
            responseBody.put("successfulCreations", successMessages.size());
            responseBody.put("failedCreations", errorMessages.size());
            responseBody.put("successDetails", successMessages);
            responseBody.put("failureDetails", errorMessages);

            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            logger.error("Error al procesar el archivo CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al procesar el archivo CSV: " + e.getMessage()));
        }
    }

    private void addError(List<Map<String, String>> errorMessages, int rowNum, String email, String message) {
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("row", String.valueOf(rowNum));
        errorDetail.put("email", email != null ? email : "N/A");
        errorDetail.put("error", message);
        errorMessages.add(errorDetail);
    }

    private ResponseEntity<?> processPrivilegedUserCreation(CreatePrivilegedUserDTO dto, int rowNumContext) {
        String context = (rowNumContext > 0) ? " (Fila CSV " + rowNumContext + ")" : "";

        if (usuarioRepository.findByEmail(dto.email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El email '" + dto.email + "' ya está registrado." + context));
        }

        Integer ciInt;
        Integer telefonoInt;

        try {
            ciInt = Integer.parseInt(dto.ci.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "El CI '" + dto.ci + "' no es un número válido." + context + " Email: " + dto.email));
        }

        try {
            telefonoInt = Integer.parseInt(dto.telefono.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("message", "El teléfono '" + dto.telefono + "' no es un número válido." + context + " Email: " + dto.email));
        }

        Usuario nuevoUsuario;

        if ("ADMINISTRADOR".equalsIgnoreCase(dto.tipoRolACrear)) {
            nuevoUsuario = new Administrador(
                    dto.nombre,
                    dto.apellido,
                    ciInt,
                    passwordEncoder.encode(dto.contrasenia),
                    dto.email,
                    telefonoInt,
                    dto.fechaNac,
                    dto.areaResponsabilidad != null ? dto.areaResponsabilidad : "General"
            );
        } else if ("VENDEDOR".equalsIgnoreCase(dto.tipoRolACrear)) {
            if (dto.codigoVendedor == null || dto.codigoVendedor.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El código de vendedor es obligatorio para crear un Vendedor." + context + " Email: " + dto.email));
            }
            nuevoUsuario = new Vendedor(
                    dto.nombre,
                    dto.apellido,
                    ciInt,
                    passwordEncoder.encode(dto.contrasenia),
                    dto.email,
                    telefonoInt,
                    dto.fechaNac,
                    dto.codigoVendedor
            );
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "Tipo de rol a crear inválido '" + dto.tipoRolACrear + "'. Debe ser ADMINISTRADOR o VENDEDOR." + context + " Email: " + dto.email));
        }

        try {
            usuarioRepository.save(nuevoUsuario);
            String tipoCreado = dto.tipoRolACrear.substring(0, 1).toUpperCase() + dto.tipoRolACrear.substring(1).toLowerCase();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "Usuario " + tipoCreado + " creado exitosamente: " + nuevoUsuario.getEmail() + "." + context));
        } catch (Exception e) {
            logger.error("Error al crear usuario privilegiado ({}) {}: {}", dto.tipoRolACrear, context, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno al crear el usuario " + dto.email + "." + context));
        }
    }

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

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<List<UserViewDTO>> getAllUsers() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        List<UserViewDTO> userViewDTOs = usuarios.stream().map(usuario -> {
            String rol = "DESCONOCIDO";
            if (usuario instanceof Administrador) {
                rol = "ADMINISTRADOR";
            } else if (usuario instanceof Vendedor) {
                rol = "VENDEDOR";
            } else if (usuario instanceof Cliente) {
                rol = "CLIENTE";
            }

            return new UserViewDTO(
                    usuario.getId(),
                    usuario.getNombre(),
                    usuario.getApellido(),
                    usuario.getEmail(),
                    usuario.getCi(),
                    usuario.getTelefono(),
                    usuario.getFechaNac(),
                    rol
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(userViewDTOs);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUserById(id);
            return ResponseEntity.noContent().build();
        } catch (UsernameNotFoundException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // --- VERSIÓN CORRECTA Y FINAL ---
    // En AdminController.java
    @GetMapping("/dashboard/statistics")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    public ResponseEntity<DashboardStatisticsDTO> getDashboardStatistics() {
        // ---- AÑADE ESTE COMENTARIO ----
        // Log para forzar el re-despliegue, versión 1.
        System.out.println(">>> Accediendo al endpoint de estadísticas v1...");
        DashboardStatisticsDTO stats = dashboardService.getDashboardStatistics();
        return ResponseEntity.ok(stats);
    }
}