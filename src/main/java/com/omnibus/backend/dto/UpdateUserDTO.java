package com.omnibus.backend.dto; // Asegúrate de que el paquete sea el correcto

// No es necesario importar LocalDate aquí si recibes la fecha como String
// import java.time.LocalDate;

public class UpdateUserDTO {
    private String nombre;
    private String apellido;
    private String ci;       // Recibir como String para flexibilidad, convertir en el servicio
    private String email;    // El backend debe manejar la lógica si el email cambia (verificación, unicidad)
    private String telefono; // Recibir como String
    private String fechaNac; // Recibir como String en formato "YYYY-MM-DD"

    // Getters
    public String getNombre() { return nombre; }
    public String getApellido() { return apellido; }
    public String getCi() { return ci; }
    public String getEmail() { return email; }
    public String getTelefono() { return telefono; }
    public String getFechaNac() { return fechaNac; }

    // Setters (útiles para testing o si construyes el DTO manualmente en algún caso)
    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public void setCi(String ci) { this.ci = ci; }
    public void setEmail(String email) { this.email = email; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public void setFechaNac(String fechaNac) { this.fechaNac = fechaNac; }
}