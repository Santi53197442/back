package com.omnibus.backend.model;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDate;
import java.time.LocalDateTime; // Asegúrate de importar LocalDateTime
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "usuarios")
public class Usuario implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false, unique = true)
    private Integer ci;

    @Column(nullable = false)
    private String contrasenia;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private Integer telefono;

    @Column(nullable = false)
    private LocalDate fechaNac;

    @Column(nullable = false)
    private String rol;

    // --- CAMPOS PARA RECUPERACIÓN DE CONTRASEÑA ---
    @Column(name = "reset_password_token") // Opcional: define el nombre de la columna explícitamente
    private String resetPasswordToken;

    @Column(name = "reset_password_token_expiry_date") // Opcional: define el nombre de la columna explícitamente
    private LocalDateTime resetPasswordTokenExpiryDate;
    // ---------------------------------------------

    // Constructores
    public Usuario() {
        this.rol = "ROLE_CLIENTE";
    }

    public Usuario(String nombre, String apellido, Integer ci, String contrasenia,
                   String email, Integer telefono, LocalDate fechaNac, String rol) {
        this.nombre = nombre;
        this.apellido = apellido;
        this.ci = ci;
        this.contrasenia = contrasenia;
        this.email = email;
        this.telefono = telefono;
        this.fechaNac = fechaNac;
        this.setRol(rol);
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

    @Override
    public String getPassword() {
        return this.contrasenia;
    }

    public void setContrasenia(String contrasenia) {
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
            this.rol = "ROLE_CLIENTE";
        } else {
            if (rol.startsWith("ROLE_")) {
                this.rol = rol.toUpperCase();
            } else {
                this.rol = "ROLE_" + rol.toUpperCase();
            }
        }
    }

    // --- GETTERS Y SETTERS PARA LOS NUEVOS CAMPOS ---
    public String getResetPasswordToken() {
        return resetPasswordToken;
    }

    public void setResetPasswordToken(String resetPasswordToken) {
        this.resetPasswordToken = resetPasswordToken;
    }

    public LocalDateTime getResetPasswordTokenExpiryDate() {
        return resetPasswordTokenExpiryDate;
    }

    public void setResetPasswordTokenExpiryDate(LocalDateTime resetPasswordTokenExpiryDate) {
        this.resetPasswordTokenExpiryDate = resetPasswordTokenExpiryDate;
    }
    // ---------------------------------------------

    // --- Implementación de métodos UserDetails ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(this.rol));
    }

    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}