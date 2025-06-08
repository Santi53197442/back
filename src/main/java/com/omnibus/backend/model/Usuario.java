// src/main/java/com/omnibus/backend/model/Usuario.java
package com.omnibus.backend.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "usuarios") // Mantenemos el nombre de tabla original para la clase base
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Usuario implements UserDetails { // Clase abstracta

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    @Column(nullable = false, unique = true)
    private Integer ci;

    @Column(nullable = false, length = 255)
    private String contrasenia;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private Integer telefono;

    @Column(nullable = false)
    private LocalDate fechaNac;

    @Column(name = "reset_password_token", length = 100)
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry_date")
    private LocalDateTime resetPasswordTokenExpiryDate;

    @CreationTimestamp // ¡La magia está aquí!
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    // Constructores
    public Usuario() {}

    public Usuario(String nombre, String apellido, Integer ci, String contrasenia,
                   String email, Integer telefono, LocalDate fechaNac) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.ci = ci;
        this.contrasenia = contrasenia;
        this.email = email;
        this.telefono = telefono;
        this.fechaNac = fechaNac;
    }

    // Getters y Setters (igual que antes, pero sin 'rol')
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
    public Integer getCi() { return ci; }
    public void setCi(Integer ci) { this.ci = ci; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Integer getTelefono() { return telefono; }
    public void setTelefono(Integer telefono) { this.telefono = telefono; }
    public LocalDate getFechaNac() { return fechaNac; }
    public void setFechaNac(LocalDate fechaNac) { this.fechaNac = fechaNac; }
    public String getResetPasswordToken() { return resetPasswordToken; }
    public void setResetPasswordToken(String resetPasswordToken) { this.resetPasswordToken = resetPasswordToken; }
    public LocalDateTime getResetPasswordTokenExpiryDate() { return resetPasswordTokenExpiryDate; }
    public void setResetPasswordTokenExpiryDate(LocalDateTime resetPasswordTokenExpiryDate) { this.resetPasswordTokenExpiryDate = resetPasswordTokenExpiryDate; }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
    public void setFechaCreacion(LocalDateTime fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    // Métodos UserDetails
    @Override
    public String getPassword() { return this.contrasenia; }
    public void setContrasenia(String contrasenia) { this.contrasenia = contrasenia; }

    @Override
    public String getUsername() { return this.email; }

    @Override
    public abstract Collection<? extends GrantedAuthority> getAuthorities(); // Implementado por subclases

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }

    public String getNombreCompleto() {
        // Si apellido es nulo o está vacío, devuelve solo el nombre.
        if (this.apellido == null || this.apellido.isBlank()) {
            return this.nombre;
        }
        // Si no, devuelve "Nombre Apellido"
        return this.nombre + " " + this.apellido;
    }
}