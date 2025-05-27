// src/main/java/com/omnibus/backend/dto/MarcarInactivoRequest.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoBus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarcarInactivoRequest {

    @NotNull(message = "La fecha y hora de inicio de inactividad no puede ser nula.")
    @FutureOrPresent(message = "La fecha y hora de inicio debe ser en el presente o futuro.")
    private LocalDateTime inicioInactividad;

    @NotNull(message = "La fecha y hora de fin de inactividad no puede ser nula.")
    private LocalDateTime finInactividad;

    @NotNull(message = "El nuevo estado del bus no puede ser nulo.")
    private EstadoBus nuevoEstado; // Debe ser EN_MANTENIMIENTO o FUERA_DE_SERVICIO
}