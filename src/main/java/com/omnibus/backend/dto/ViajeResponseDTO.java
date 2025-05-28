// src/main/java/com/omnibus/backend/dto/ViajeResponseDTO.java
package com.omnibus.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder
public class ViajeResponseDTO {
    private Integer id;
    private LocalDate fecha;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private String origenNombre;
    private Long origenId;
    private String destinoNombre;
    private Long destinoId;
    private Long busAsignadoId;
    private String busMatricula;
    private Integer asientosDisponibles;
    private String estado; // Campo para el estado del viaje
    // private Integer capacidadTotal; // Opcional, si quieres mostrarla
}