package com.omnibus.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "omnibus") // Nombre de la tabla en la base de datos
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Omnibus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // Usar Long para IDs es común

    @NotBlank(message = "La matrícula no puede estar vacía.")
    @Size(min = 6, max = 10, message = "La matrícula debe tener entre 6 y 10 caracteres.") // Ajusta según el formato de tu país
    @Column(unique = true, nullable = false) // Matrícula debe ser única
    private String matricula;

    @NotBlank(message = "La marca no puede estar vacía.")
    private String marca;

    @NotBlank(message = "El modelo no puede estar vacío.")
    private String modelo;

    @NotNull(message = "La capacidad de asientos no puede ser nula.")
    @Min(value = 1, message = "La capacidad de asientos debe ser al menos 1.")
    private Integer capacidadAsientos; // Integer para permitir nulidad si es opcional en algún DTO (aunque aquí es NotNull)

    @NotNull(message = "El estado del bus no puede ser nulo.")
    @Enumerated(EnumType.STRING) // Guarda el nombre del enum como String en la BD
    @Column(nullable = false)
    private EstadoBus estado;

    // Relación con Localidad
    // Un ómnibus está en una localidad actual. Una localidad puede tener muchos ómnibus.
    @NotNull(message = "La localidad actual no puede ser nula.")
    @ManyToOne(fetch = FetchType.LAZY) // Carga perezosa es generalmente buena para el rendimiento
    @JoinColumn(name = "localidad_actual_id", nullable = false) // Nombre de la columna de la FK
    private Localidad localidadActual;

    // Constructor sin ID para creación (opcional, Lombok puede generarlo)
    public Omnibus(String matricula, String marca, String modelo, Integer capacidadAsientos, EstadoBus estado, Localidad localidadActual) {
        this.matricula = matricula;
        this.marca = marca;
        this.modelo = modelo;
        this.capacidadAsientos = capacidadAsientos;
        this.estado = estado;
        this.localidadActual = localidadActual;
    }
}