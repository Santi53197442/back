package com.omnibus.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank; // Para Strings
import jakarta.validation.constraints.NotNull;   // Para objetos como LocalDate
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class CreatePrivilegedUserDTO {

    @NotBlank(message = "El nombre no puede estar vacío.")
    public String nombre;

    @NotBlank(message = "El apellido no puede estar vacío.")
    public String apellido;

    // --- CAMBIO IMPORTANTE AQUÍ ---
    @NotBlank(message = "El CI no puede estar vacío.") // @NotBlank para String
    public String ci; // Debe ser String

    @NotBlank(message = "La contraseña no puede estar vacía.")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres.")
    public String contrasenia;

    @NotBlank(message = "El email no puede estar vacío.")
    @Email(message = "El email debe ser válido.")
    public String email;

    // --- CAMBIO IMPORTANTE AQUÍ ---
    @NotBlank(message = "El teléfono no puede estar vacío.") // @NotBlank para String
    public String telefono; // Debe ser String

    @NotNull(message = "La fecha de nacimiento no puede ser nula.")
    public LocalDate fechaNac;

    @NotBlank(message = "El tipo de rol a crear no puede estar vacío.")
    public String tipoRolACrear; // "ADMINISTRADOR" o "VENDEDOR"

    // Específico para Administrador, opcional
    public String areaResponsabilidad;

    // Específico para Vendedor, será validado como obligatorio en la lógica si tipoRolACrear es VENDEDOR
    public String codigoVendedor;

    // Getters y Setters son opcionales si los campos son públicos y no hay lógica adicional,
    // pero si los tienes, asegúrate de que reflejen los tipos correctos.
}