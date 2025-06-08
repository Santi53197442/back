// src/main/java/com/omnibus/backend/model/Cliente.java
package com.omnibus.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;   // <-- NUEVA IMPORTACIÓN
import jakarta.persistence.Enumerated; // <-- NUEVA IMPORTACIÓN
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "clientes_data")
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Cliente extends Usuario {

    // --- NUEVO CAMPO ---
    @Enumerated(EnumType.STRING) // Guarda el nombre del enum ("COMUN", "JUBILADO") en la DB, no el número.
    @Column(name = "tipo_cliente", nullable = false) // La columna no puede ser nula.
    private TipoCliente tipo;
    // -------------------

    public Cliente() {
        super();
    }

    // --- CONSTRUCTOR MODIFICADO ---
    public Cliente(String nombre, String apellido, Integer ci, String contrasenia,
                   String email, Integer telefono, LocalDate fechaNac, TipoCliente tipo) { // <-- Se añade el parámetro
        super(nombre, apellido, ci, contrasenia, email, telefono, fechaNac);
        this.tipo = tipo; // <-- Se asigna el nuevo campo
    }

    // --- NUEVOS GETTERS Y SETTERS ---
    public TipoCliente getTipo() {
        return tipo;
    }

    public void setTipo(TipoCliente tipo) {
        this.tipo = tipo;
    }
    // ------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(RoleType.CLIENTE.getAuthority()));
    }
}