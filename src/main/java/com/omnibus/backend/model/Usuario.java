package com.omnibus.backend.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "usuarios")
public class Usuario implements UserDetails { // <-- Implementa UserDetails
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false, unique = true)
    private Integer ci; // Considera String si puede tener ceros a la izquierda o caracteres no numéricos

    @Column(nullable = false)
    private String contrasenia; // Esta será la contraseña HASHED

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private Integer telefono; // Considera String si puede tener prefijos, etc.

    @Column(nullable = false)
    private LocalDate fechaNac;

    @Column(nullable = false)
    private String rol;  // Ej: "ROLE_CLIENTE", "ROLE_ADMINISTRADOR"

    // Constructores
    public Usuario() {
        // Rol por defecto con el prefijo ROLE_
        this.rol = "ROLE_CLIENTE";
    }

    public Usuario(String nombre, String apellido, Integer ci, String contrasenia,
                   String email, Integer telefono, LocalDate fechaNac, String rol) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.ci = ci;
        this.contrasenia = contrasenia; // La contraseña ya debería venir hasheada para el constructor
        this.email = email;
        this.telefono = telefono;
        this.fechaNac = fechaNac;
        // Asegurar el formato del rol al construir el objeto
        this.setRol(rol); // Usar el setter para la lógica de formateo del rol
    }

    // Getters y Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public Integer getCi() {
        return ci;
    }

    public void setCi(Integer ci) {
        this.ci = ci;
    }

    // El getter para la contraseña es requerido por UserDetails
    @Override
    public String getPassword() {
        return this.contrasenia;
    }

    public void setContrasenia(String contrasenia) {
        // La contraseña debe ser hasheada ANTES de llamar a este setter
        // por el PasswordEncoder en el servicio o controlador.
        this.contrasenia = contrasenia;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getTelefono() {
        return telefono;
    }

    public void setTelefono(Integer telefono) {
        this.telefono = telefono;
    }

    public LocalDate getFechaNac() {
        return fechaNac;
    }

    public void setFechaNac(LocalDate fechaNac) {
        this.fechaNac = fechaNac;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        if (rol == null || rol.trim().isEmpty()) {
            this.rol = "ROLE_CLIENTE"; // Rol por defecto
        } else {
            // Asegurar que el rol tenga el prefijo ROLE_ y esté en mayúsculas (convención)
            if (rol.startsWith("ROLE_")) {
                this.rol = rol.toUpperCase();
            } else {
                this.rol = "ROLE_" + rol.toUpperCase();
            }
        }
    }

    // --- Implementación de métodos UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // El campo 'rol' ya debe tener el formato "ROLE_NOMBRE_ROL"
        return Collections.singletonList(new SimpleGrantedAuthority(this.rol));
    }

    @Override
    public String getUsername() {
        return this.email; // Usaremos el email como username para Spring Security
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // Puedes añadir lógica real aquí si es necesario
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // Puedes añadir lógica real aquí si es necesario
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // Puedes añadir lógica real aquí si es necesario
    }

    @Override
    public boolean isEnabled() {
        return true; // Puedes añadir lógica real aquí si es necesario (ej. verificación de email)
    }
}