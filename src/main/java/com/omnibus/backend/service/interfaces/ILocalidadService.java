package com.omnibus.backend.service.interfaces;

import com.omnibus.backend.dto.LocalidadDTO;
import com.omnibus.backend.exceptions.CsvProcessingException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ILocalidadService {
    LocalidadDTO crearLocalidad(LocalidadDTO localidadDto);
    List<LocalidadDTO> crearLocalidadesDesdeCSV(MultipartFile file) throws CsvProcessingException, IOException;
    List<LocalidadDTO> obtenerTodasLasLocalidades();
    LocalidadDTO obtenerLocalidadPorId(Long id);
}
