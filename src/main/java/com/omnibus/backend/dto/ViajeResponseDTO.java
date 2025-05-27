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
    private Integer id; // El ID del Viaje puede seguir siendo Integer
    private LocalDate fecha;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private String origenNombre;
    private Long origenId;      // <<--- CAMBIADO A Long
    private String destinoNombre;
    private Long destinoId;     // <<--- CAMBIADO A Long
    private Long busAsignadoId;
    private String busMatricula;
    private Integer asientosDisponibles;
}