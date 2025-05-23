// src/main/java/com/omnibus/backend/dto/CreatePrivilegedUserDTO.java
package com.omnibus.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class CreatePrivilegedUserDTO {

    @NotBlank(message = "El nombre es obligatorio")
    public String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    public String apellido;

    @NotNull(message = "El CI es obligatorio")
    public Integer ci; // Asumiendo que sigue siendo Integer

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El formato del email es inválido")
    public String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    public String contrasenia;

    @NotNull(message = "El teléfono es obligatorio")
    public Integer telefono; // Asumiendo que sigue siendo Integer

    @NotNull(message = "La fecha de nacimiento es obligatoria")
    public LocalDate fechaNac;

    @NotBlank(message = "El tipo de rol a crear es obligatorio (ADMINISTRADOR o VENDEDOR)")
    public String tipoRolACrear; // "ADMINISTRADOR" o "VENDEDOR"

    // Campos opcionales específicos (puedes añadirlos según necesidad)
    public String codigoVendedor; // Solo si tipoRolACrear es VENDEDOR
    public String areaResponsabilidad; // Solo si tipoRolACrear es ADMINISTRADOR

    // Getters y Setters o constructores si los prefieres
}