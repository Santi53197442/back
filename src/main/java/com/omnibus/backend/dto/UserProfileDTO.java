package com.omnibus.backend.dto;

import com.omnibus.backend.model.Administrador;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.model.Vendedor;

import java.time.format.DateTimeFormatter;

public class UserProfileDTO {
    private Long id; // <<-- CAMBIO #1: AÑADIDO
    private String email;
    private String nombre;
    private String apellido;
    private String ci;
    private String telefono;
    private String fechaNac;
    private String rol;

    // Constructor
    public UserProfileDTO() {}
    public UserProfileDTO(Usuario usuario) {
        this.id = usuario.getId(); // <<-- CAMBIO #2: AÑADIDO
        this.email = usuario.getEmail();
        this.nombre = usuario.getNombre();
        this.apellido = usuario.getApellido();
        this.ci = usuario.getCi() != null ? String.valueOf(usuario.getCi()) : "";
        this.telefono = usuario.getTelefono() != null ? String.valueOf(usuario.getTelefono()) : "";

        if (usuario.getFechaNac() != null) {
            this.fechaNac = usuario.getFechaNac().format(DateTimeFormatter.ISO_LOCAL_DATE);
        } else {
            this.fechaNac = "";
        }

        if (usuario instanceof Administrador) {
            this.rol = "administrador";
        } else if (usuario instanceof Vendedor) {
            this.rol = "vendedor";
        } else if (usuario instanceof Cliente) {
            this.rol = "cliente";
        } else {
            this.rol = "desconocido";
        }
    }

    // Getters
    public Long getId() { // <<-- CAMBIO #3: AÑADIDO
        return id;
    }
    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getCi() { return ci; }
    public String getTelefono() { return telefono; }
    public String getFechaNac() { return fechaNac; }
    public String getRol() { return rol; }
}