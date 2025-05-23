package com.omnibus.backend.dto; // Asegúrate de que el paquete sea el correcto

import java.time.format.DateTimeFormatter;
import com.omnibus.backend.model.Usuario; // Asumiendo que Usuario tiene los campos necesarios

public class UserProfileDTO {
    private String email;
    private String nombre;
    private String apellido;
    private String ci;
    private String telefono;
    private String fechaNac; // Formato YYYY-MM-DD
    private String rol;      // Rol formateado para el frontend (ej. "admin")

    // Constructor que toma la entidad Usuario
    public UserProfileDTO(Usuario usuario) {
        this.email = usuario.getEmail();
        this.nombre = usuario.getNombre();
        this.apellido = usuario.getApellido();
        this.ci = usuario.getCi() != null ? String.valueOf(usuario.getCi()) : ""; // Convertir a String
        this.telefono = usuario.getTelefono() != null ? String.valueOf(usuario.getTelefono()) : ""; // Convertir a String

        if (usuario.getFechaNac() != null) {
            this.fechaNac = usuario.getFechaNac().format(DateTimeFormatter.ISO_LOCAL_DATE); // Formato YYYY-MM-DD
        } else {
            this.fechaNac = "";
        }

        // Formatear el rol para el frontend
        String rolCompleto = usuario.getRol();
        if (rolCompleto != null && rolCompleto.startsWith("ROLE_")) {
            this.rol = rolCompleto.substring(5).toLowerCase();
        } else {
            this.rol = rolCompleto != null ? rolCompleto.toLowerCase() : "";
        }
    }

    // Getters (necesarios para la serialización JSON)
    public String getEmail() { return email; }
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getCi() { return ci; }
    public String getTelefono() { return telefono; }
    public String getFechaNac() { return fechaNac; }
    public String getRol() { return rol; }

    // Setters (generalmente no necesarios para DTOs de respuesta si se construyen una vez)
}