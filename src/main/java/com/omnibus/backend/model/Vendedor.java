// src/main/java/com/omnibus/backend/model/Vendedor.java
package com.omnibus.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table(name = "vendedores_data") // Nombre de tabla diferente
@PrimaryKeyJoinColumn(name = "usuario_id")
public class Vendedor extends Usuario { // Extiende de Usuario

    @Column(name = "codigo_vendedor", unique = true, length = 50)
    private String codigoVendedor;

    public Vendedor() {
        super();
    }

    public Vendedor(String nombre, String apellido, Integer ci, String contrasenia,
                    String email, Integer telefono, LocalDate fechaNac, String codigoVendedor) {
        super(nombre, apellido, ci, contrasenia, email, telefono, fechaNac);
        this.codigoVendedor = codigoVendedor;
    }

    public String getCodigoVendedor() { return codigoVendedor; }
    public void setCodigoVendedor(String codigoVendedor) { this.codigoVendedor = codigoVendedor; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(RoleType.VENDEDOR.getAuthority()));
    }
}