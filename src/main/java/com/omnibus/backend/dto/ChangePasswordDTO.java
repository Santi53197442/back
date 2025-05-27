package com.omnibus.backend.dto;

import jakarta.validation.constraints.NotBlank; // O javax.validation.constraints.NotBlank si usas una versión anterior de Spring Boot
import jakarta.validation.constraints.Size;

public class ChangePasswordDTO {

    @NotBlank(message = "La contraseña actual no puede estar vacía.")
    private String currentPassword;

    @NotBlank(message = "La nueva contraseña no puede estar vacía.")
    @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres.") // Ejemplo de validación
    private String newPassword;

    // Getters y Setters
    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}