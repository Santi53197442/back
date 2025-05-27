// src/main/java/com/omnibus/backend/controller/VendedorController.java
package com.omnibus.backend.controller;

// Imports para Localidad
import com.omnibus.backend.dto.CreateLocalidadDTO;
import com.omnibus.backend.model.Localidad;
import com.omnibus.backend.service.LocalidadService;

// Imports para Ómnibus
import com.omnibus.backend.dto.CreateOmnibusDTO;
import com.omnibus.backend.dto.MarcarInactivoRequest; // NUEVO DTO
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.model.Viaje; // Para la respuesta de error
import com.omnibus.backend.service.OmnibusService;
import com.omnibus.backend.exception.BusConViajesAsignadosException; // NUEVA EXCEPCIÓN

// Imports para Viaje
import com.omnibus.backend.dto.ViajeRequestDTO;
import com.omnibus.backend.dto.ViajeResponseDTO;
import com.omnibus.backend.service.ViajeService;

// Imports comunes y de validación/CSV
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendedor") // La ruta base para todos los endpoints de este controlador
public class VendedorController {

    private static final Logger logger = LoggerFactory.getLogger(VendedorController.class);

    private final LocalidadService localidadService;
    private final OmnibusService omnibusService;
    private final ViajeService viajeService;
    private final Validator validator;

    @Autowired
    public VendedorController(LocalidadService localidadService,
                              OmnibusService omnibusService,
                              ViajeService viajeService,
                              Validator validator) {
        this.localidadService = localidadService;
        this.omnibusService = omnibusService;
        this.viajeService = viajeService;
        this.validator = validator;
    }

    // --- Endpoints de Localidad (existentes) ---
    @PostMapping("/localidades")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaLocalidad(@Valid @RequestBody CreateLocalidadDTO createLocalidadDTO) {
        // ... (código existente sin cambios)
        try {
            Localidad nuevaLocalidad = localidadService.crearLocalidad(createLocalidadDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(nuevaLocalidad);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al crear localidad individual: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al crear la localidad."));
        }
    }

    @PostMapping("/localidades-batch")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaLocalidadBatch(@RequestParam("file") MultipartFile file) {
        // ... (código existente sin cambios)
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El archivo CSV no puede estar vacío."));
        }

        List<String> successMessages = new ArrayList<>();
        List<Map<String, String>> errorMessages = new ArrayList<>();
        int processedDataRows = 0;
        String[] expectedHeaders = {"nombre", "departamento", "direccion"};

        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El archivo CSV está vacío o no tiene cabeceras."));
            }
            for (String expectedHeader : expectedHeaders) {
                if (!headerMap.containsKey(expectedHeader.toLowerCase())) { // toLowerCase para ser indulgente con el case
                    return ResponseEntity.badRequest().body(Map.of("message", "Cabecera faltante en el CSV para localidades: " + expectedHeader));
                }
            }

            for (CSVRecord csvRecord : csvParser) {
                processedDataRows++;
                CreateLocalidadDTO dto = new CreateLocalidadDTO();
                String nombreLocalidadActual = "N/A";
                try {
                    dto.setNombre(csvRecord.get("nombre"));
                    nombreLocalidadActual = dto.getNombre(); // Actualizar después de obtenerlo
                    dto.setDepartamento(csvRecord.get("departamento"));
                    dto.setDireccion(csvRecord.get("direccion"));

                    Set<ConstraintViolation<CreateLocalidadDTO>> violations = validator.validate(dto);
                    if (!violations.isEmpty()) {
                        String errorDetails = violations.stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.joining(", "));
                        addLocalidadError(errorMessages, processedDataRows, nombreLocalidadActual, "Error de validación: " + errorDetails);
                        continue;
                    }

                    localidadService.crearLocalidad(dto);
                    successMessages.add("Fila " + processedDataRows + ": Localidad '" + dto.getNombre() + "' creada exitosamente.");

                } catch (IllegalArgumentException e) { // Conflicto, nombre duplicado
                    addLocalidadError(errorMessages, processedDataRows, nombreLocalidadActual, e.getMessage());
                } catch (Exception e) { // Otros errores inesperados
                    logger.error("Error procesando fila de datos {} del CSV para localidades: {}", processedDataRows, e.getMessage(), e);
                    addLocalidadError(errorMessages, processedDataRows, nombreLocalidadActual, "Error inesperado: " + e.getMessage());
                }
            }

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("totalDataRowsProcessed", processedDataRows);
            responseBody.put("successfulCreations", successMessages.size());
            responseBody.put("failedCreations", errorMessages.size());
            responseBody.put("successDetails", successMessages);
            responseBody.put("failureDetails", errorMessages);
            if (processedDataRows == 0 && errorMessages.isEmpty()) {
                responseBody.put("message", "El archivo CSV no contenía filas de datos de localidades para procesar después de las cabeceras.");
            }
            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            logger.error("Error al procesar el archivo CSV de localidades: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al procesar el archivo CSV de localidades: " + e.getMessage()));
        }
    }

    private void addLocalidadError(List<Map<String, String>> errorMessages, int dataRowNum, String nombreLocalidad, String message) {
        // ... (código existente sin cambios)
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("row", String.valueOf(dataRowNum));
        errorDetail.put("nombreLocalidad", nombreLocalidad != null ? nombreLocalidad : "N/A");
        errorDetail.put("error", message);
        errorMessages.add(errorDetail);
    }

    @GetMapping("/localidades-disponibles")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<List<Localidad>> listarTodasLasLocalidadesParaSeleccion() {
        // ... (código existente sin cambios)
        try {
            List<Localidad> localidades = localidadService.obtenerTodasLasLocalidades();
            return ResponseEntity.ok(localidades);
        } catch (Exception e) {
            logger.error("Error al listar localidades disponibles para el vendedor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    // --- Fin Endpoints de Localidad ---


    // --- Endpoints de Ómnibus (con adiciones) ---
    // Ruta base para ómnibus dentro de vendedor: /api/vendedor/omnibus
    @PostMapping("/omnibus")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaOmnibus(@Valid @RequestBody CreateOmnibusDTO createOmnibusDTO) {
        // ... (código existente sin cambios)
        try {
            Omnibus nuevoOmnibus = omnibusService.crearOmnibus(createOmnibusDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(nuevoOmnibus);
        } catch (IllegalArgumentException e) { // Para matrícula duplicada
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) { // Para localidad no encontrada
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al crear ómnibus por vendedor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al crear el ómnibus."));
        }
    }


    @PostMapping("/omnibus-batch")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaOmnibusBatch(@RequestParam("file") MultipartFile file) {
        // ... (código existente sin cambios)
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El archivo CSV para ómnibus no puede estar vacío."));
        }

        List<String> successMessages = new ArrayList<>();
        List<Map<String, String>> errorMessages = new ArrayList<>();
        int processedDataRows = 0;
        String[] expectedHeaders = {"matricula", "marca", "modelo", "capacidadAsientos", "estado", "localidadActualId"};

        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {

            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El archivo CSV de ómnibus está vacío o no tiene cabeceras."));
            }
            for (String expectedHeader : expectedHeaders) {
                if (!headerMap.containsKey(expectedHeader.toLowerCase())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Cabecera faltante en el CSV para ómnibus: " + expectedHeader));
                }
            }

            for (CSVRecord csvRecord : csvParser) {
                processedDataRows++;
                CreateOmnibusDTO dto = new CreateOmnibusDTO();
                String matriculaActual = "N/A";

                try {
                    matriculaActual = csvRecord.get("matricula");
                    dto.setMatricula(matriculaActual);
                    dto.setMarca(csvRecord.get("marca"));
                    dto.setModelo(csvRecord.get("modelo"));

                    try {
                        dto.setCapacidadAsientos(Integer.parseInt(csvRecord.get("capacidadAsientos")));
                    } catch (NumberFormatException e) {
                        addOmnibusError(errorMessages, processedDataRows, matriculaActual, "Valor de 'capacidadAsientos' no es un número válido.");
                        continue;
                    }

                    try {
                        dto.setEstado(EstadoBus.valueOf(csvRecord.get("estado").toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        addOmnibusError(errorMessages, processedDataRows, matriculaActual, "Valor de 'estado' inválido. Revisa los valores permitidos para EstadoBus.");
                        continue;
                    }

                    try {
                        dto.setLocalidadActualId(Long.parseLong(csvRecord.get("localidadActualId")));
                    } catch (NumberFormatException e) {
                        addOmnibusError(errorMessages, processedDataRows, matriculaActual, "Valor de 'localidadActualId' no es un número válido.");
                        continue;
                    }

                    Set<ConstraintViolation<CreateOmnibusDTO>> violations = validator.validate(dto);
                    if (!violations.isEmpty()) {
                        String errorDetails = violations.stream()
                                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                .collect(Collectors.joining(", "));
                        addOmnibusError(errorMessages, processedDataRows, matriculaActual, "Error de validación: " + errorDetails);
                        continue;
                    }

                    omnibusService.crearOmnibus(dto);
                    successMessages.add("Fila " + processedDataRows + ": Ómnibus con matrícula '" + dto.getMatricula() + "' creado exitosamente.");

                } catch (IllegalArgumentException e) { // Matrícula duplicada
                    addOmnibusError(errorMessages, processedDataRows, matriculaActual, e.getMessage());
                } catch (EntityNotFoundException e) { // Localidad no encontrada
                    addOmnibusError(errorMessages, processedDataRows, matriculaActual, e.getMessage());
                } catch (Exception e) {
                    logger.error("Error procesando fila de datos {} del CSV para ómnibus (matrícula {}): {}",
                            processedDataRows, matriculaActual, e.getMessage(), e);
                    addOmnibusError(errorMessages, processedDataRows, matriculaActual, "Error inesperado procesando la fila: " + e.getMessage());
                }
            }

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("totalDataRowsProcessed", processedDataRows);
            responseBody.put("successfulCreations", successMessages.size());
            responseBody.put("failedCreations", errorMessages.size());
            responseBody.put("successDetails", successMessages);
            responseBody.put("failureDetails", errorMessages);
            if (processedDataRows == 0 && errorMessages.isEmpty()) {
                responseBody.put("message", "El archivo CSV no contenía filas de datos de ómnibus para procesar después de las cabeceras.");
            }
            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            logger.error("Error al procesar el archivo CSV de ómnibus: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error al procesar el archivo CSV de ómnibus: " + e.getMessage()));
        }
    }

    private void addOmnibusError(List<Map<String, String>> errorMessages, int dataRowNum, String matricula, String message) {
        // ... (código existente sin cambios)
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("row", String.valueOf(dataRowNum));
        errorDetail.put("matricula", matricula != null ? matricula : "N/A");
        errorDetail.put("error", message);
        errorMessages.add(errorDetail);
    }

    @GetMapping("/omnibusListar") // Mantengo tu endpoint existente para listar
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')") // ADMIN también debería poder listar
    public ResponseEntity<List<Omnibus>> listarTodosLosOmnibus() {
        try {
            List<Omnibus> omnibusLista = omnibusService.obtenerTodosLosOmnibus();
            return ResponseEntity.ok(omnibusLista);
        } catch (Exception e) {
            logger.error("Error al listar todos los ómnibus: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- NUEVOS ENDPOINTS PARA GESTIONAR ESTADO DE ÓMNIBUS ---
    @PutMapping("/omnibus/{id}/marcar-inactivo")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> marcarOmnibusInactivo(
            @PathVariable Long id,
            @Valid @RequestBody MarcarInactivoRequest request) {
        try {
            logger.info("Solicitud para marcar ómnibus {} inactivo desde {} hasta {} como {}",
                    id, request.getInicioInactividad(), request.getFinInactividad(), request.getNuevoEstado());
            Omnibus omnibusActualizado = omnibusService.marcarOmnibusInactivo(
                    id,
                    request.getInicioInactividad(),
                    request.getFinInactividad(),
                    request.getNuevoEstado()
            );
            logger.info("Ómnibus {} marcado como {} exitosamente.", id, request.getNuevoEstado());
            return ResponseEntity.ok(omnibusActualizado);
        } catch (BusConViajesAsignadosException e) {
            logger.warn("Conflicto al marcar ómnibus {} inactivo: {}", id, e.getMessage());
            // Devuelve los viajes conflictivos para que el frontend pueda mostrarlos
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("message", e.getMessage());
            errorBody.put("viajesConflictivos", e.getViajesConflictivos()); // Podrías mapear a DTOs si es necesario
            return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody);
        } catch (EntityNotFoundException e) {
            logger.warn("No se pudo marcar ómnibus inactivo. No encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("Argumento inválido al marcar ómnibus {} inactivo: {}", id, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al marcar ómnibus {} inactivo: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al procesar la solicitud."));
        }
    }

    @PutMapping("/omnibus/{id}/marcar-operativo")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> marcarOmnibusOperativo(@PathVariable Long id) {
        try {
            logger.info("Solicitud para marcar ómnibus {} como OPERATIVO.", id);
            Omnibus omnibusActualizado = omnibusService.marcarOmnibusOperativo(id);
            logger.info("Ómnibus {} marcado como OPERATIVO exitosamente.", id);
            return ResponseEntity.ok(omnibusActualizado);
        } catch (EntityNotFoundException e) {
            logger.warn("No se pudo marcar ómnibus operativo. No encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) { // Por ej. si intentas marcar operativo un bus ASIGNADO_A_VIAJE
            logger.warn("No se pudo marcar ómnibus {} operativo. Estado ilegal: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al marcar ómnibus {} operativo: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al procesar la solicitud."));
        }
    }
    // --- Fin Endpoints de Ómnibus ---


    // --- Endpoints de Viaje (existentes) ---
    @PostMapping("/viajes")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaViaje(@Valid @RequestBody ViajeRequestDTO viajeRequestDTO) {
        // ... (código existente sin cambios)
        try {
            logger.info("Recibida solicitud para crear viaje: Fecha={}, OrigenId={}, DestinoId={}",
                    viajeRequestDTO.getFecha(), viajeRequestDTO.getOrigenId(), viajeRequestDTO.getDestinoId());
            ViajeResponseDTO nuevoViaje = viajeService.crearViaje(viajeRequestDTO);
            logger.info("Viaje creado exitosamente con ID: {}", nuevoViaje.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(nuevoViaje);
        } catch (ViajeService.NoBusDisponibleException e) {
            logger.warn("No se pudo crear el viaje. No hay bus disponible: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            logger.warn("No se pudo crear el viaje. Entidad no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("No se pudo crear el viaje. Argumento inválido: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al crear el viaje: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al crear el viaje."));
        }
    }

    @PostMapping("/viajes/{viajeId}/finalizar")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> finalizarViaje(@PathVariable Integer viajeId) {
        // ... (código existente sin cambios)
        try {
            logger.info("Recibida solicitud para finalizar viaje con ID: {}", viajeId);
            viajeService.finalizarViaje(viajeId);
            logger.info("Viaje {} finalizado exitosamente.", viajeId);
            return ResponseEntity.ok(Map.of("message", "Viaje " + viajeId + " finalizado exitosamente."));
        } catch (EntityNotFoundException e) {
            logger.warn("No se pudo finalizar el viaje. Viaje no encontrado: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("No se pudo finalizar el viaje. Estado ilegal: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al finalizar el viaje {}: {}", viajeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al finalizar el viaje."));
        }
    }

    // NUEVO ENDPOINT para obtener ómnibus por estado
    @GetMapping("/omnibus/por-estado") // ej: /api/omnibus/por-estado?estado=OPERATIVO
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMINISTRADOR')") // Ajusta los roles según necesidad
    public ResponseEntity<List<Omnibus>> obtenerOmnibusPorEstado(@RequestParam("estado") EstadoBus estado) {
        try {
            // Necesitarás un nuevo método en OmnibusService y OmnibusRepository
            List<Omnibus> omnibusLista = omnibusService.obtenerOmnibusPorEstado(estado);
            if (omnibusLista.isEmpty()) {
                // Puedes devolver 204 No Content o 200 OK con lista vacía
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(omnibusLista);
        } catch (Exception e) {
            logger.error("Error al listar ómnibus por estado {}: {}", estado, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    // --- Fin Endpoints de Viaje ---
}