package com.omnibus.backend.model;

import com.omnibus.backend.enums.EstadoOmnibus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "omnibus")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Omnibus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10) // Ajusta length si es necesario
    private String matricula;

    @Column(length = 100)
    private String marca;

    @Column(length = 100)
    private String modelo;

    @Column(nullable = false)
    private int capacidadAsientos;

    @ManyToOne(fetch = FetchType.LAZY) // LAZY para no cargar siempre la localidad innecesariamente
    @JoinColumn(name = "localidad_actual_id", nullable = false)
    private Localidad localidadActual;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoOmnibus estado;

    // Constructor útil para la creación inicial
    public Omnibus(String matricula, String modelo, int capacidadAsientos, Localidad localidadActual, EstadoOmnibus estado) {
        this.matricula = matricula;
        this.modelo = modelo;
        this.capacidadAsientos = capacidadAsientos;
        this.localidadActual = localidadActual;
        this.estado = estado;
    }
}