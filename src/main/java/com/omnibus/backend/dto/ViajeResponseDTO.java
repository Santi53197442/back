// src/main/java/com/omnibus/backend/dto/ViajeResponseDTO.java
package com.omnibus.backend.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter; // Aunque con @Builder, los setters no son estrictamente necesarios si solo lo construyes
import lombok.AllArgsConstructor; // Útil con @Builder
import lombok.NoArgsConstructor;  // Útil con @Builder

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter // Si vas a modificar instancias después de crearlas, si no, @Builder es suficiente para la creación.
@Builder
@NoArgsConstructor  // Necesario si usas @AllArgsConstructor junto con @Builder para ciertos casos o frameworks.
@AllArgsConstructor // Permite un constructor con todos los argumentos, que Lombok @Builder utiliza.
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

    // --- NUEVO CAMPO PRECIO ---
    private Double precio;

    // private Integer capacidadTotal; // Opcional, si quieres mostrarla
}