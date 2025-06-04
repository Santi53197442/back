// src/main/java/com/omnibus/backend/dto/ViajeDetalleConAsientosDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoViaje;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List; // Si cambias numerosAsientoOcupados a List
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViajeDetalleConAsientosDTO {
    // Información del Viaje
    private Integer id;
    private LocalDate fecha;
    private LocalTime horaSalida;
    private LocalTime horaLlegada;
    private String origenNombre;
    // private Long origenId; // Opcional, si el frontend lo necesita
    private String destinoNombre;
    // private Long destinoId; // Opcional
    private Double precio; // Precio por asiento
    private EstadoViaje estado; // El Enum directamente está bien para el DTO

    // Información del Ómnibus
    // private Long omnibusId; // Opcional
    private String omnibusMatricula;
    private int capacidadOmnibus;

    // Información de Asientos
    private int asientosDisponibles; // <--- CAMPO AÑADIDO
    private Set<Integer> numerosAsientoOcupados; // Números de los asientos ya vendidos/reservados/utilizados
}