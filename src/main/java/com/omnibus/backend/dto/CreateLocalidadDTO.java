package com.omnibus.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLocalidadDTO {

    @NotBlank(message = "El nombre de la localidad es obligatorio")
    private String nombre;

    @NotBlank(message = "El departamento es obligatorio")
    private String departamento;

    @NotBlank(message = "La dirección es obligatoria")
    private String direccion;
}