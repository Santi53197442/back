// src/main/java/com/omnibus/backend/model/Cliente.java
package com.omnibus.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient; // <-- IMPORTAR @Transient
// import jakarta.persistence.Column; // Si añades campos específicos
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections; // O List.of() para Java 9+

@Entity
@Table(name = "clientes_data")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Cliente extends Usuario {

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
    @Transient // <--- AÑADIR ESTA ANOTACIÓN
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Devuelve el rol ROLE_CLIENTE para la seguridad
        return Collections.singletonList(new SimpleGrantedAuthority(RoleType.CLIENTE.getAuthority()));
        // O si usas Java 9+: return List.of(new SimpleGrantedAuthority(RoleType.CLIENTE.getAuthority()));
    }
}