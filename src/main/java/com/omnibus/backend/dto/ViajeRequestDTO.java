package com.omnibus.backend.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive; // Para el precio
import lombok.Getter;
import lombok.Setter;
import lombok.ToString; // Para un toString útil

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
@ToString // Para facilitar el logging y debugging del DTO
public class ViajeRequestDTO {

    @NotNull(message = "La fecha del viaje es obligatoria")
    @FutureOrPresent(message = "La fecha del viaje no puede ser en el pasado")
    private LocalDate fecha;

    @NotNull(message = "La hora de salida es obligatoria")
    private LocalTime horaSalida;

    @NotNull(message = "La hora de llegada es obligatoria")
    private LocalTime horaLlegada;

    @NotNull(message = "El ID de la localidad de origen es obligatorio")
    private Long origenId;

    @NotNull(message = "El ID de la localidad de destino es obligatorio")
    private Long destinoId;

    // --- NUEVO CAMPO PRECIO EN DTO ---
    @NotNull(message = "El precio del viaje es obligatorio.")
    @Positive(message = "El precio del viaje debe ser un valor positivo.")
    private Double precio;

    // Lombok genera getters y setters. No se necesitan constructores explícitos
    // a menos que quieras lógica personalizada en ellos.
}