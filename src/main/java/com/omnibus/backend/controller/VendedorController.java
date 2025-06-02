// src/main/java/com/omnibus/backend/controller/VendedorController.java
package com.omnibus.backend.controller;

// Imports para Localidad
import com.omnibus.backend.dto.*;
import com.omnibus.backend.model.Localidad;
import com.omnibus.backend.service.LocalidadService;

// Imports para Ómnibus
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.service.OmnibusService;
import com.omnibus.backend.exception.BusConViajesAsignadosException;

// Imports para Viaje
import com.omnibus.backend.model.EstadoViaje; // IMPORTANTE
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
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/vendedor")
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

    // --- Endpoints de Localidad ---
    // ... (tu código existente de localidades) ...
    @PostMapping("/localidades")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaLocalidad(@Valid @RequestBody CreateLocalidadDTO createLocalidadDTO) {
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
                if (!headerMap.containsKey(expectedHeader.toLowerCase())) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Cabecera faltante en el CSV para localidades: " + expectedHeader));
                }
            }
            for (CSVRecord csvRecord : csvParser) {
                processedDataRows++;
                CreateLocalidadDTO dto = new CreateLocalidadDTO();
                String nombreLocalidadActual = "N/A";
                try {
                    dto.setNombre(csvRecord.get("nombre"));
                    nombreLocalidadActual = dto.getNombre();
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
                } catch (IllegalArgumentException e) {
                    addLocalidadError(errorMessages, processedDataRows, nombreLocalidadActual, e.getMessage());
                } catch (Exception e) {
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
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("row", String.valueOf(dataRowNum));
        errorDetail.put("nombreLocalidad", nombreLocalidad != null ? nombreLocalidad : "N/A");
        errorDetail.put("error", message);
        errorMessages.add(errorDetail);
    }
    @GetMapping("/localidades-disponibles")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<List<Localidad>> listarTodasLasLocalidadesParaSeleccion() {
        try {
            List<Localidad> localidades = localidadService.obtenerTodasLasLocalidades();
            return ResponseEntity.ok(localidades);
        } catch (Exception e) {
            logger.error("Error al listar localidades disponibles para el vendedor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // --- Endpoints de Ómnibus ---
    // ... (tu código existente de ómnibus) ...
    @PostMapping("/omnibus")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaOmnibus(@Valid @RequestBody CreateOmnibusDTO createOmnibusDTO) {
        try {
            Omnibus nuevoOmnibus = omnibusService.crearOmnibus(createOmnibusDTO);
            return ResponseEntity.status(HttpStatus.CREATED).body(nuevoOmnibus);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (EntityNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error al crear ómnibus por vendedor: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al crear el ómnibus."));
        }
    }
    @PostMapping("/omnibus-batch")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaOmnibusBatch(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "El archivo CSV para ómnibus no puede estar vacío."));
        }
        List<String> successMessages = new ArrayList<>();
        List<Map<String, String>> errorMessages = new ArrayList<>();
        int processedDataRows = 0;
        String[] expectedHeaders = {"matricula", "marca", "modelo", "capacidadasientos", "estado", "localidadactualid"};
        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader,
                     CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreHeaderCase().withTrim())) {
            Map<String, Integer> headerMap = csvParser.getHeaderMap();
            if (headerMap == null || headerMap.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "El archivo CSV de ómnibus está vacío o no tiene cabeceras."));
            }
            for (String expectedHeader : expectedHeaders) {
                if (!headerMap.containsKey(expectedHeader)) {
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
                } catch (IllegalArgumentException | EntityNotFoundException e) {
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
        Map<String, String> errorDetail = new HashMap<>();
        errorDetail.put("row", String.valueOf(dataRowNum));
        errorDetail.put("matricula", matricula != null ? matricula : "N/A");
        errorDetail.put("error", message);
        errorMessages.add(errorDetail);
    }
    @GetMapping("/omnibusListar")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<List<Omnibus>> listarTodosLosOmnibus() {
        try {
            List<Omnibus> omnibusLista = omnibusService.obtenerTodosLosOmnibus();
            return ResponseEntity.ok(omnibusLista);
        } catch (Exception e) {
            logger.error("Error al listar todos los ómnibus: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
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
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("message", e.getMessage());
            errorBody.put("viajesConflictivos", e.getViajesConflictivos());
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
            logger.info("Ómnibus {} marcado como OPERATIVO exitosamente. Estado anterior: {}, Nuevo estado: {}",
                    id, omnibusActualizado.getEstado(), EstadoBus.OPERATIVO); // Se asume que el servicio lo cambia a OPERATIVO
            return ResponseEntity.ok(omnibusActualizado);
        } catch (EntityNotFoundException e) {
            logger.warn("No se pudo marcar ómnibus {} operativo. No encontrado: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("No se pudo marcar ómnibus {} operativo. Condición no permitida: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al marcar ómnibus {} operativo: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al procesar la solicitud."));
        }
    }
    @GetMapping("/omnibus/por-estado")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> obtenerOmnibusPorEstado(@RequestParam("estado") String estadoStr) {
        try {
            EstadoBus estado;
            try {
                estado = EstadoBus.valueOf(estadoStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Estado de búsqueda de ómnibus inválido: '{}'. Valores permitidos: {}", estadoStr, java.util.Arrays.toString(EstadoBus.values()));
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("message", "Valor para 'estado' inválido: " + estadoStr + ".");
                errorBody.put("allowedValues", java.util.Arrays.stream(EstadoBus.values())
                        .map(Enum::name)
                        .collect(Collectors.toList()));
                return ResponseEntity.badRequest().body(errorBody);
            }
            logger.info("Solicitud para obtener ómnibus por estado: {}", estado);
            List<Omnibus> omnibusLista = omnibusService.obtenerOmnibusPorEstado(estado);
            if (omnibusLista.isEmpty()) {
                logger.info("No se encontraron ómnibus con estado {}", estado);
                return ResponseEntity.ok(new ArrayList<Omnibus>()); // Devolver lista vacía con OK
            }
            logger.info("Encontrados {} ómnibus con estado {}", omnibusLista.size(), estado);
            return ResponseEntity.ok(omnibusLista);
        } catch (Exception e) {
            logger.error("Error interno al listar ómnibus por estado '{}': {}", estadoStr, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno del servidor al procesar la solicitud."));
        }
    }

    // --- Endpoints de Viaje ---
    @PostMapping("/viajes")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> altaViaje(@Valid @RequestBody ViajeRequestDTO viajeRequestDTO) {
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

    @PutMapping("/viajes/{viajeId}/reasignar")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> reasignarViajeAOmnibus(
            @PathVariable Integer viajeId,
            @Valid @RequestBody ReasignarViajeRequestDTO reasignarRequest) {
        try {
            logger.info("Solicitud para reasignar viaje ID {} al ómnibus ID {}", viajeId, reasignarRequest.getNuevoOmnibusId());
            ViajeResponseDTO viajeActualizado = viajeService.reasignarViaje(viajeId, reasignarRequest.getNuevoOmnibusId());
            logger.info("Viaje ID {} reasignado exitosamente al ómnibus ID {}", viajeId, reasignarRequest.getNuevoOmnibusId());
            return ResponseEntity.ok(viajeActualizado);
        } catch (EntityNotFoundException e) {
            logger.warn("No se pudo reasignar el viaje. Entidad no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("No se pudo reasignar el viaje. Argumento inválido: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("No se pudo reasignar el viaje. Estado ilegal del viaje: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (ViajeService.NoBusDisponibleException e) {
            logger.warn("No se pudo reasignar el viaje. Nuevo bus no disponible: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al reasignar el viaje {}: {}", viajeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Error interno al reasignar el viaje."));
        }
    }

    @GetMapping("/viajes/estado")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> obtenerViajesPorEstado(@RequestParam("estado") String estadoViajeStr) {
        try {
            EstadoViaje estado;
            try {
                estado = EstadoViaje.valueOf(estadoViajeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warn("Estado de búsqueda de viaje inválido: '{}'. Valores permitidos: {}", estadoViajeStr, java.util.Arrays.toString(EstadoViaje.values()));
                Map<String, Object> errorBody = new HashMap<>();
                errorBody.put("message", "Valor para 'estado' de viaje inválido: " + estadoViajeStr + ".");
                errorBody.put("allowedValues", java.util.Arrays.stream(EstadoViaje.values())
                        .map(Enum::name)
                        .collect(Collectors.toList()));
                return ResponseEntity.badRequest().body(errorBody);
            }
            logger.info("Solicitud para obtener viajes por estado: {}", estado);
            List<ViajeResponseDTO> viajes = viajeService.obtenerViajesPorEstado(estado);
            if (viajes.isEmpty()) {
                logger.info("No se encontraron viajes con estado {}", estado);
                return ResponseEntity.ok(new ArrayList<ViajeResponseDTO>());
            }
            logger.info("Encontrados {} viajes con estado {}", viajes.size(), estado);
            return ResponseEntity.ok(viajes);
        } catch (Exception e) {
            logger.error("Error interno al listar viajes por estado '{}': {}", estadoViajeStr, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno del servidor al procesar la solicitud de viajes por estado."));
        }
    }

    @GetMapping("/omnibus/{omnibusId}/viajes")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN')")
    public ResponseEntity<?> listarViajesDeOmnibus(
            @PathVariable Long omnibusId,
            @Valid @ModelAttribute BusquedaViajesOmnibusDTO busquedaDTO) {
        try {
            logger.info("Solicitud para listar viajes del ómnibus ID {} con criterios: {}", omnibusId, busquedaDTO.toString());
            List<ViajeResponseDTO> viajes = viajeService.buscarViajesDeOmnibus(omnibusId, busquedaDTO);
            // No es necesario verificar si la lista está vacía aquí, el servicio ya lo hace y devuelve lista vacía.
            // El frontend interpretará una lista vacía como "sin resultados".
            return ResponseEntity.ok(viajes);
        } catch (EntityNotFoundException e) {
            logger.warn("No se pudieron listar viajes. Entidad no encontrada: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al listar viajes del ómnibus {}: {}", omnibusId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno al procesar la solicitud de viajes del ómnibus."));
        }
    }


    @GetMapping("/viajes/buscar-disponibles")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN') or hasRole('CLIENTE')") // El cliente también podría necesitar esto
    public ResponseEntity<?> buscarViajesConDisponibilidad(
            @Valid @ModelAttribute BusquedaViajesGeneralDTO criteriosBusqueda) { // Usar @ModelAttribute para GET con múltiples params
        try {
            logger.info("Iniciando búsqueda de viajes con disponibilidad. Criterios: {}", criteriosBusqueda);
            List<ViajeConDisponibilidadDTO> viajes = viajeService.buscarViajesConDisponibilidad(criteriosBusqueda);

            if (viajes.isEmpty()) {
                logger.info("No se encontraron viajes con los criterios especificados.");
                // Devolver 204 No Content o 200 OK con lista vacía, según preferencia.
                // 200 OK con lista vacía es a menudo más fácil para los clientes.
                return ResponseEntity.ok(new ArrayList<>());
            }

            logger.info("Encontrados {} viajes con disponibilidad.", viajes.size());
            return ResponseEntity.ok(viajes);
        } catch (IllegalArgumentException e) {
            // Esto podría ser lanzado por validaciones en el DTO o en el servicio
            logger.warn("Argumentos inválidos para la búsqueda de viajes: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al buscar viajes con disponibilidad: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno del servidor al procesar la búsqueda de viajes."));
        }
    }


    @GetMapping("/viajes/{viajeId}/detalles-asientos")
    @PreAuthorize("hasRole('VENDEDOR') or hasRole('ADMIN') or hasRole('CLIENTE')") // Permitir a roles relevantes
    public ResponseEntity<?> obtenerDetallesViajeConAsientos(@PathVariable Integer viajeId) {
        try {
            logger.info("Solicitud de detalles y asientos para el viaje ID: {}", viajeId);
            ViajeDetalleConAsientosDTO detalles = viajeService.obtenerDetallesViajeParaSeleccionAsientos(viajeId);
            return ResponseEntity.ok(detalles);
        } catch (EntityNotFoundException e) {
            logger.warn("Viaje no encontrado al obtener detalles y asientos: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            logger.warn("Estado ilegal al obtener detalles y asientos para el viaje {}: {}", viajeId, e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Error interno al obtener detalles y asientos del viaje {}: {}", viajeId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Error interno al obtener los detalles del viaje y asientos."));
        }
    }
}