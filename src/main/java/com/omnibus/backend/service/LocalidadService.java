package com.omnibus.backend.service;

import com.omnibus.backend.model.Localidad;
import com.omnibus.backend.dto.LocalidadDTO;
import com.omnibus.backend.service.interfaces.ILocalidadService;
import com.omnibus.backend.repository.ILocalidadRepository;


import com.omnibus.backend.exceptions.CsvProcessingException;
import com.omnibus.backend.exceptions.ResourceAlreadyExistsException;
import com.omnibus.backend.exceptions.ResourceNotFoundException;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LocalidadService implements ILocalidadService {

    private static final Logger logger = LoggerFactory.getLogger(LocalidadService.class);

    @Autowired
    private ILocalidadRepository iLocalidadRepository;

    // --- Mappers ---
    private LocalidadDTO mapToDto(Localidad localidad) {
        if (localidad == null) return null;
        return new LocalidadDTO(localidad.getId(), localidad.getNombre());
    }

    private Localidad mapToEntity(LocalidadDTO dto) {
        if (dto == null) return null;
        Localidad localidad = new Localidad();
        localidad.setNombre(dto.getNombre().trim()); // Asegurar trim
        return localidad;
    }

    @Override
    @Transactional
    public LocalidadDTO crearLocalidad(LocalidadDTO localidadDto) {
        String nombreLocalidad = localidadDto.getNombre().trim();
        logger.info("Intentando crear localidad con nombre: '{}'", nombreLocalidad);
        if (iLocalidadRepository.findByNombre(nombreLocalidad).isPresent()) {
            logger.warn("Localidad ya existe con nombre: '{}'", nombreLocalidad);
            throw new ResourceAlreadyExistsException("Localidad ya existe con el nombre: " + nombreLocalidad);
        }
        Localidad localidad = new Localidad(nombreLocalidad);

        Localidad savedLocalidad = iLocalidadRepository.save(localidad);
        logger.info("Localidad '{}' creada con ID: {}", savedLocalidad.getNombre(), savedLocalidad.getId());
        return mapToDto(savedLocalidad);
    }

    @Override
    @Transactional
    public List<LocalidadDTO> crearLocalidadesDesdeCSV(MultipartFile file) throws CsvProcessingException, IOException {
        logger.info("Procesando archivo CSV para crear localidades: {}", file.getOriginalFilename());
        List<Localidad> nuevasLocalidadesParaGuardar = new ArrayList<>();
        Set<String> nombresEnCSV = new HashSet<>(); // Para detectar duplicados dentro del CSV
        List<String> erroresDeValidacion = new ArrayList<>();

        // Asumimos que el CSV tiene una cabecera y la columna se llama "nombre_localidad"
        // Ajustar `withHeader` si el nombre de la columna es diferente o `withSkipHeaderRecord(false)` si no hay cabecera.
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader("nombre_localidad") // Nombre esperado de la columna en la cabecera
                .setSkipHeaderRecord(true)     // Saltar la primera línea (cabecera)
                .setTrim(true)                 // Quitar espacios en blanco
                .setIgnoreEmptyLines(true)     // Ignorar líneas vacías
                .build();

        try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser csvParser = new CSVParser(fileReader, csvFormat)) {

            for (CSVRecord csvRecord : csvParser) {
                // Validar consistencia del registro (si se espera una columna y viene otra cosa)
                if (!csvRecord.isConsistent()) {
                    erroresDeValidacion.add("Línea " + csvRecord.getRecordNumber() + ": formato de registro inconsistente.");
                    continue;
                }

                String nombreLocalidad;
                try {
                    nombreLocalidad = csvRecord.get("nombre_localidad"); // Obtener por nombre de cabecera
                } catch (IllegalArgumentException e) {
                    // Esto sucede si la cabecera "nombre_localidad" no se encuentra en el archivo
                    throw new CsvProcessingException("El archivo CSV debe contener una columna con la cabecera 'nombre_localidad'.");
                }


                if (nombreLocalidad.isEmpty()) {
                    erroresDeValidacion.add("Línea " + csvRecord.getRecordNumber() + ": nombre de localidad vacío.");
                    continue;
                }
                if (nombreLocalidad.length() < 2 || nombreLocalidad.length() > 100) {
                    erroresDeValidacion.add("Línea " + csvRecord.getRecordNumber() + ": Localidad '" + nombreLocalidad + "' no cumple longitud (2-100 caracteres).");
                    continue;
                }

                if (nombresEnCSV.contains(nombreLocalidad.toLowerCase())) {
                    erroresDeValidacion.add("Línea " + csvRecord.getRecordNumber() + ": Localidad '" + nombreLocalidad + "' duplicada dentro del archivo CSV.");
                    continue;
                }

                if (iLocalidadRepository.findByNombre(nombreLocalidad).isPresent()) {
                    erroresDeValidacion.add("Línea " + csvRecord.getRecordNumber() + ": Localidad '" + nombreLocalidad + "' ya existe en la base de datos.");
                    continue;
                }

                nombresEnCSV.add(nombreLocalidad.toLowerCase());
                Localidad nuevaLocalidad = new Localidad();
                nuevaLocalidad.setNombre(nombreLocalidad);
                nuevasLocalidadesParaGuardar.add(nuevaLocalidad);
            }
        } catch (IOException e) {
            logger.error("Error de IO al leer el archivo CSV: " + e.getMessage(), e);
            throw new CsvProcessingException("Error al leer el archivo CSV.", e);
        }

        if (!erroresDeValidacion.isEmpty()) {
            logger.warn("Errores de validación encontrados en el CSV (no se guardó nada): {}", String.join("; ", erroresDeValidacion));
            throw new CsvProcessingException("Se encontraron errores en el archivo CSV: " + String.join("; ", erroresDeValidacion));
        }

        if (nuevasLocalidadesParaGuardar.isEmpty()) {
            logger.info("No se encontraron nuevas localidades válidas para agregar desde el CSV.");
            return new ArrayList<>();
        }

        List<Localidad> localidadesGuardadas = iLocalidadRepository.saveAll(nuevasLocalidadesParaGuardar);
        logger.info("{} localidades guardadas exitosamente desde el archivo CSV.", localidadesGuardadas.size());
        return localidadesGuardadas.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<LocalidadDTO> obtenerTodasLasLocalidades() {
        logger.debug("Obteniendo todas las localidades");
        return iLocalidadRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LocalidadDTO obtenerLocalidadPorId(Long id) {
        logger.debug("Obteniendo localidad por ID: {}", id);
        Localidad localidad = iLocalidadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Localidad no encontrada con id: " + id));
        return mapToDto(localidad);
    }
}
