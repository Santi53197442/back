package com.omnibus.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
// También podrías usar @Data en lugar de @Getter y @Setter si quieres equals, hashCode, toString
// import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@Builder // Esta anotación es la que genera el patrón builder
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
    private String estado; // <-- CAMPO AÑADIDO
}