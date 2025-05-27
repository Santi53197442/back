package com.omnibus.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "localidades")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Localidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El nombre de la localidad no puede estar vacío")
    private String nombre;

    @NotBlank(message = "El departamento no puede estar vacío") // Añade validación si es obligatorio
    private String departamento;

    @NotBlank(message = "La dirección no puede estar vacía") // Añade validación si es obligatorio
    private String direccion;

    // Constructor si lo necesitas para inicializar sin ID (por ejemplo, en el servicio)
    public Localidad(String nombre, String departamento, String direccion) {
        this.nombre = nombre;
        this.departamento = departamento;
        this.direccion = direccion;
    }
}