package com.omnibus.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)  // Definir la estrategia de herencia
@Table(name = "usuarios")
public class Usuario {
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
    private String rol;  // Rol del usuario (Administrador, Vendedor, Cliente)

    // Constructores
    public Usuario() {
        this.rol = "Cliente";  // Asignamos Cliente como rol predeterminado
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
        this.rol = rol != null ? rol : "Cliente"; // Asegura que el rol predeterminado sea Cliente
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

    public String getContrasenia() {
        return contrasenia;
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
        this.rol = rol;
    }
}
