package com.omnibus.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocalidadDTO {
    private Long id;
    @NotBlank(message = "El nombre de la localidad no puede estar vacío.")
    @Size(min = 2, max = 100, message = "El nombre de la localidad debe tener entre 2 y 100 caracteres.")
    private String nombre;
}
