// src/main/java/com/omnibus/backend/dto/AuthResponseDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.TipoCliente;

public class AuthResponseDTO {
    private String token;
    private Long id; // <--- AÑADE ESTE CAMPO PARA EL ID NUMÉRICO
    private String email;
    private String rol;
    private String nombre;
    private String apellido;
    private String ci;
    private String telefono;
    private String fechaNac; // String en formato YYYY-MM-DD
    private TipoCliente tipoCliente;

    // Constructor por defecto (buena práctica)
    public AuthResponseDTO() {}

    // Constructor Actualizado para incluir el ID
    public AuthResponseDTO(String token, Long id, String email, String rol, String nombre, String apellido, String ci, String telefono, String fechaNac, TipoCliente tipoCliente) {
        this.token = token;
        this.id = id; // <--- ASIGNAR ID
        this.email = email;
        this.rol = rol;
        this.nombre = nombre;
        this.apellido = apellido;
        this.ci = ci;
        this.telefono = telefono;
        this.fechaNac = fechaNac;
        this.tipoCliente = tipoCliente;
    }

    // Getters
    public String getToken() { return token; }
    public Long getId() { return id; } // <--- GETTER PARA ID
    public String getEmail() { return email; }
    public String getRol() { return rol; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getCi() { return ci; }
    public String getTelefono() { return telefono; }
    public String getFechaNac() { return fechaNac; }
    public TipoCliente getTipoCliente() { return tipoCliente; }


    // Setters (si los usas)
    public void setToken(String token) { this.token = token; }
    public void setId(Long id) { this.id = id; } // <--- SETTER PARA ID
    public void setEmail(String email) { this.email = email; }
    public void setRol(String rol) { this.rol = rol; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public void setCi(String ci) { this.ci = ci; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public void setFechaNac(String fechaNac) { this.fechaNac = fechaNac; }
    public void setTipoCliente(TipoCliente tipoCliente) { this.tipoCliente = tipoCliente; }

}