package com.omnibus.backend.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ViajeRequestDTO {

    @NotNull(message = "La fecha del viaje es obligatoria")
    @FutureOrPresent(message = "La fecha del viaje no puede ser en el pasado")
    private LocalDate fecha;

    @NotNull(message = "La hora de salida es obligatoria")
    private LocalTime horaSalida;

    @NotNull(message = "La hora de llegada es obligatoria")
    private LocalTime horaLlegada;

    @NotNull(message = "El ID de la localidad de origen es obligatorio")
    private Long origenId; // <<--- CAMBIADO A Long

    @NotNull(message = "El ID de la localidad de destino es obligatorio")
    private Long destinoId; // <<--- CAMBIADO A Long
}