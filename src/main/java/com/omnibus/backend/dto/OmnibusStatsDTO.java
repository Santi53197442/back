// src/main/java/com/omnibus/backend/dto/OmnibusStatsDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoBus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OmnibusStatsDTO {
    private EstadoBus estado;
    private Integer capacidadAsientos;
    private String marca;
    private String localidadActualNombre;
}