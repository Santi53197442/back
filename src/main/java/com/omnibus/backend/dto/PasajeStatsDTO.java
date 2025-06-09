// src/main/java/com/omnibus/backend/dto/PasajeStatsDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoPasaje;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasajeStatsDTO {
    private Double precio;
    private EstadoPasaje estado;
    private LocalDate fechaViaje;
    private String ruta; // Ej: "Montevideo - Salto"
}