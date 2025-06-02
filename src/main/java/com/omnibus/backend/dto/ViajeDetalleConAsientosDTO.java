// src/main/java/com/omnibus/backend/dto/ViajeDetalleConAsientosDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoViaje; // Asegúrate que la importación sea correcta
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set; // O List, Set es bueno si no quieres duplicados

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
    private String destinoNombre;
    private Double precio; // Precio por asiento
    private EstadoViaje estado;

    // Información del Ómnibus
    private String omnibusMatricula;
    private int capacidadOmnibus;

    // Información de Asientos
    private Set<Integer> numerosAsientoOcupados; // Números de los asientos ya vendidos/reservados
}