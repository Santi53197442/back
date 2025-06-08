// src/main/java/com/omnibus/backend/dto/ViajePrecioDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoViaje;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data // Incluye @Getter, @Setter, @ToString, @EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class ViajePrecioDTO {

    private Integer id;
    private LocalDate fecha;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private String origenNombre;
    private String destinoNombre;
    private String matriculaBus;
    private Integer asientosDisponibles;
    private EstadoViaje estado;
    private Double precio;

}