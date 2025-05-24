package com.omnibus.backend.controller;

import com.omnibus.backend.dto.LocalidadDTO;

import com.omnibus.backend.exceptions.CsvProcessingException; // Asegúrate de importar tus excepciones
import com.omnibus.backend.service.interfaces.ILocalidadService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map; // Para un mensaje de error más estructurado

@RestController
@RequestMapping("/api/vendedor")
@PreAuthorize("hasRole('VENDEDOR')") // Para cuando implementes seguridad
public class VendedorController {

    private static final Logger logger = LoggerFactory.getLogger(VendedorController.class);

    @Autowired
    private ILocalidadService iLocalidadService;

    @PostMapping
    public ResponseEntity<LocalidadDTO> crearLocalidad(@Valid @RequestBody LocalidadDTO localidadDto) {
        logger.info("API: Solicitud para crear localidad: {}", localidadDto.getNombre());
        LocalidadDTO nuevaLocalidad = iLocalidadService.crearLocalidad(localidadDto);
        return new ResponseEntity<>(nuevaLocalidad, HttpStatus.CREATED);
    }

    @PostMapping("/csv")
    public ResponseEntity<?> crearLocalidadesDesdeCSV(@RequestParam("file") MultipartFile file) {
        logger.info("API: Solicitud para crear localidades desde archivo CSV: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            logger.warn("API: Archivo CSV vacío recibido.");
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo CSV no puede estar vacío."));
        }

        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("text/csv") && !contentType.equals("application/vnd.ms-excel") && !file.getOriginalFilename().toLowerCase().endsWith(".csv"))) {
            logger.warn("API: Tipo de archivo no válido: {}. Se esperaba CSV.", contentType);
            return ResponseEntity.badRequest().body(Map.of("error", "Formato de archivo no válido. Solo se aceptan archivos CSV."));
        }

        try {
            List<LocalidadDTO> localidadesCreadas = iLocalidadService.crearLocalidadesDesdeCSV(file);
            if (localidadesCreadas.isEmpty()) {
                logger.info("API: No se crearon nuevas localidades desde el CSV (podría estar vacío o contener solo duplicados/errores ya manejados por el servicio).");
                return ResponseEntity.ok().body(Map.of("message", "No se crearon nuevas localidades (posiblemente ya existían todas o el archivo no contenía datos válidos procesables)."));
            }
            logger.info("API: {} localidades creadas exitosamente desde CSV.", localidadesCreadas.size());
            return new ResponseEntity<>(localidadesCreadas, HttpStatus.CREATED);
        } catch (CsvProcessingException e) {
            logger.error("API: Error procesando CSV: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            logger.error("API: Error de IO al procesar CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Error interno del servidor al procesar el archivo."));
        }
    }

    // GETTERS que ya tenías
    @GetMapping
    public ResponseEntity<List<LocalidadDTO>> obtenerTodasLasLocalidades() {
        logger.info("API: Solicitud para obtener todas las localidades.");
        List<LocalidadDTO> localidades = iLocalidadService.obtenerTodasLasLocalidades();
        return ResponseEntity.ok(localidades);
    }

    @GetMapping("/{id}")
    public ResponseEntity<LocalidadDTO> obtenerLocalidadPorId(@PathVariable Long id) {
        logger.info("API: Solicitud para obtener localidad con ID: {}", id);
        LocalidadDTO localidad = iLocalidadService.obtenerLocalidadPorId(id); // Esto ya lanza ResourceNotFound si no existe
        return ResponseEntity.ok(localidad);
    }
}