// src/main/java/com/omnibus/backend/model/Administrador.java
package com.omnibus.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections; // O java.util.List si son múltiples

@Entity
@Table(name = "administradores_data") // Nombre de tabla diferente
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Administrador extends Usuario { // Extiende de Usuario

    @Column(name = "area_responsabilidad", length = 100)
    private String areaResponsabilidad;

    public Administrador() {
        super();
    }

    public Administrador(String nombre, String apellido, Integer ci, String contrasenia,
                         String email, Integer telefono, LocalDate fechaNac, String areaResponsabilidad) {
        super(nombre, apellido, ci, contrasenia, email, telefono, fechaNac);
        this.areaResponsabilidad = areaResponsabilidad;
    }

    public String getAreaResponsabilidad() { return areaResponsabilidad; }
    public void setAreaResponsabilidad(String areaResponsabilidad) { this.areaResponsabilidad = areaResponsabilidad; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(RoleType.ADMINISTRADOR.getAuthority()));
    }
}