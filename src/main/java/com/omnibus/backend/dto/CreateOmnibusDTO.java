package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoBus; // Importa tu enum
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOmnibusDTO {

    @NotBlank(message = "La matrícula no puede estar vacía.")
    @Size(min = 6, max = 10, message = "La matrícula debe tener entre 6 y 10 caracteres.")
    private String matricula;

    @NotBlank(message = "La marca no puede estar vacía.")
    private String marca;

    @NotBlank(message = "El modelo no puede estar vacío.")
    private String modelo;

    @NotNull(message = "La capacidad de asientos no puede ser nula.")
    @Min(value = 1, message = "La capacidad de asientos debe ser al menos 1.")
    private Integer capacidadAsientos;

    @NotNull(message = "El estado del bus no puede ser nulo.")
    private EstadoBus estado; // El frontend enviará el string del enum (ej. "OPERATIVO")

    @NotNull(message = "El ID de la localidad actual no puede ser nulo.")
    private Long localidadActualId; // Recibimos el ID de la localidad
}