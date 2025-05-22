package com.omnibus.backend.dto;

public class AuthResponseDTO {
    private String token;
    private String email; // El campo username en UserDetails suele ser el email
    private String rol;   // Rol formateado para el frontend (ej: "admin", "user")
    private String nombre;
    private String apellido;

    // Constructor
    public AuthResponseDTO(String token, String email, String rol, String nombre, String apellido) {
        this.token = token;
        this.email = email;
        this.rol = rol;
        this.nombre = nombre;
        this.apellido = apellido;
    }

    // Getters
    public String getToken() {
        return token;
    }

    public String getEmail() {
        return email;
    }

    public String getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }

    public String getApellido() {
        return apellido;
    }

    // Setters (opcionales, generalmente no necesarios para DTOs de respuesta)
    public void setToken(String token) {
        this.token = token;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }
}