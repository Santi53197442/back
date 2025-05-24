package com.omnibus.backend.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "localidades")
@Setter
@Getter
public class Localidad {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true) // unique = true también es importante aquí
    private String nombre;

    // Constructor
    public Localidad() {}

    public Localidad(String nombre) {
        this.nombre = nombre;
    }

}
