package com.omnibus.backend.dto;

import java.time.LocalDate;

public class UserViewDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private Integer ci; // O String si así lo manejas
    private Integer telefono; // O String
    private LocalDate fechaNac;
    private String rol; // Para simplificar, ej: "ADMINISTRADOR", "VENDEDOR", "CLIENTE"
    // Añade otros campos que quieras mostrar, como 'codigoVendedor' o 'areaResponsabilidad' si es relevante

    // Constructores
    public UserViewDTO() {}

    public UserViewDTO(Long id, String nombre, String apellido, String email, Integer ci, Integer telefono, LocalDate fechaNac, String rol) {
        this.id = id;
        this.nombre = nombre;
        this.apellido = apellido;
        this.email = email;
        this.ci = ci;
        this.telefono = telefono;
        this.fechaNac = fechaNac;
        this.rol = rol;
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Integer getCi() { return ci; }
    public void setCi(Integer ci) { this.ci = ci; }
    public Integer getTelefono() { return telefono; }
    public void setTelefono(Integer telefono) { this.telefono = telefono; }
    public LocalDate getFechaNac() { return fechaNac; }
    public void setFechaNac(LocalDate fechaNac) { this.fechaNac = fechaNac; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
}
