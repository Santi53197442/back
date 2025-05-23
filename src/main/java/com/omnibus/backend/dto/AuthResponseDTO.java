package com.omnibus.backend.dto;

public class AuthResponseDTO {
    private String token;
    private String email;    // El campo username en UserDetails suele ser el email
    private String rol;      // Rol formateado para el frontend (ej: "admin", "user")
    private String nombre;
    private String apellido;
    private String ci;       // <-- NUEVO CAMPO
    private String telefono; // <-- NUEVO CAMPO
    private String fechaNac; // <-- NUEVO CAMPO (String en formato YYYY-MM-DD)

    // Constructor Actualizado
    public AuthResponseDTO(String token, String email, String rol, String nombre, String apellido, String ci, String telefono, String fechaNac) {
        this.token = token;
        this.email = email;
        this.rol = rol;
        this.nombre = nombre;
        this.apellido = apellido;
        this.ci = ci;                   // <-- ASIGNAR
        this.telefono = telefono;       // <-- ASIGNAR
        this.fechaNac = fechaNac;     // <-- ASIGNAR
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

    public String getCi() { // <-- NUEVO GETTER
        return ci;
    }

    public String getTelefono() { // <-- NUEVO GETTER
        return telefono;
    }

    public String getFechaNac() { // <-- NUEVO GETTER
        return fechaNac;
    }

    // Setters (opcionales, pero si los tienes, añade para los nuevos campos también)
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

    public void setCi(String ci) { // <-- NUEVO SETTER
        this.ci = ci;
    }

    public void setTelefono(String telefono) { // <-- NUEVO SETTER
        this.telefono = telefono;
    }

    public void setFechaNac(String fechaNac) { // <-- NUEVO SETTER
        this.fechaNac = fechaNac;
    }
}