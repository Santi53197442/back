// src/main/java/com/omnibus/backend/model/Cliente.java
package com.omnibus.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
// import jakarta.persistence.Column; // Si añades campos específicos
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "clientes_data") // Nombre de tabla diferente para datos específicos de cliente
@PrimaryKeyJoinColumn(name = "usuario_id") // FK a la tabla 'usuarios' (la de la clase base)
public class Cliente extends Usuario { // Extiende de Usuario

    // @Column(name = "puntos_fidelidad")
    // private Integer puntosFidelidad;

    public Cliente() {
        super();
    }

    public Cliente(String nombre, String apellido, Integer ci, String contrasenia,
                   String email, Integer telefono, LocalDate fechaNac /*, Integer puntosFidelidad */) {
        super(nombre, apellido, ci, contrasenia, email, telefono, fechaNac);
        // this.puntosFidelidad = puntosFidelidad;
    }

    // Getters y Setters para campos específicos
    // public Integer getPuntosFidelidad() { return puntosFidelidad; }
    // public void setPuntosFidelidad(Integer puntosFidelidad) { this.puntosFidelidad = puntosFidelidad; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(RoleType.CLIENTE.getAuthority())); // Usando el Enum RoleType
    }
}