package com.omnibus.backend.dto;

import com.omnibus.backend.model.Administrador; // Importar subclases
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Usuario;     // Importar la clase base Usuario
import com.omnibus.backend.model.Vendedor;
// import com.omnibus.backend.model.RoleType; // Si quieres usar el enum para los strings de rol

import java.time.format.DateTimeFormatter;

public class UserProfileDTO {
    private String email;
    private String nombre;
    private String apellido;
    private String ci;       // String
    private String telefono; // String
    private String fechaNac; // Formato YYYY-MM-DD
    private String rol;      // Rol formateado para el frontend (ej. "cliente", "vendedor", "administrador")

    // Constructor que toma la entidad Usuario (que será una instancia de Cliente, Vendedor, o Administrador)
    public UserProfileDTO(Usuario usuario) { // El parámetro es de tipo Usuario (clase base)
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

        // Determinar y formatear el rol basado en el tipo de instancia
        if (usuario instanceof Administrador) {
            this.rol = "administrador"; // O RoleType.ADMINISTRADOR.name().toLowerCase() si usas el enum
        } else if (usuario instanceof Vendedor) {
            this.rol = "vendedor";    // O RoleType.VENDEDOR.name().toLowerCase()
        } else if (usuario instanceof Cliente) {
            this.rol = "cliente";     // O RoleType.CLIENTE.name().toLowerCase()
        } else {
            // Si por alguna razón es una instancia de Usuario pero no de una subclase conocida
            // (lo cual no debería pasar con la configuración de herencia JOINED y Usuario abstracto)
            // o si quieres un fallback.
            // Podrías también tomarlo de getAuthorities() si solo esperas un rol.
            this.rol = "desconocido";
            // Opcionalmente, si quieres obtener el rol de las authorities (más genérico):
            // if (!usuario.getAuthorities().isEmpty()) {
            //     String authority = usuario.getAuthorities().iterator().next().getAuthority();
            //     if (authority.startsWith("ROLE_")) {
            //         this.rol = authority.substring(5).toLowerCase();
            //     } else {
            //         this.rol = authority.toLowerCase();
            //     }
            // }
        }

        // Aquí podrías añadir lógica para obtener campos específicos de las subclases si los necesitas en el DTO
        // Ejemplo:
        // if (usuario instanceof Cliente) {
        //     Cliente cliente = (Cliente) usuario;
        //     // this.puntosFidelidad = cliente.getPuntosFidelidad(); // Si tuvieras este campo en el DTO
        // }
    }

    // Getters (necesarios para la serialización JSON por Jackson/Spring MVC)
    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getCi() { return ci; }
    public String getTelefono() { return telefono; }
    public String getFechaNac() { return fechaNac; }
    public String getRol() { return rol; }

    // Setters (generalmente no son necesarios para DTOs de respuesta si son inmutables post-construcción)
    // public void setEmail(String email) { this.email = email; }
    // ...etc.
}