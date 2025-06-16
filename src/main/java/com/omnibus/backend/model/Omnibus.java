package com.omnibus.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime; // Importar LocalDateTime

@Entity
@Table(name = "omnibus")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Omnibus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "La matrícula no puede estar vacía.")
    @Size(min = 6, max = 10, message = "La matrícula debe tener entre 6 y 10 caracteres.")
    @Column(unique = true, nullable = false)
    private String matricula;

    @NotBlank(message = "La marca no puede estar vacía.")
    private String marca;

    @NotBlank(message = "El modelo no puede estar vacío.")
    private String modelo;

    @NotNull(message = "La capacidad de asientos no puede ser nula.")
    @Min(value = 1, message = "La capacidad de asientos debe ser al menos 1.")
    private Integer capacidadAsientos;

    @NotNull(message = "El estado del bus no puede ser nulo.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoBus estado;

    @NotNull(message = "La localidad actual no puede ser nula.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "localidad_actual_id", nullable = false)
    private Localidad localidadActual;

    // --- NUEVOS CAMPOS PARA INACTIVIDAD PROGRAMADA ---
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_programado", nullable = true)
    private EstadoBus estadoProgramado;

    @Column(name = "inicio_inactividad_programada", nullable = true)
    private LocalDateTime inicioInactividadProgramada;

    @Column(name = "fin_inactividad_programada", nullable = true)
    private LocalDateTime finInactividadProgramada;
    // --- FIN DE NUEVOS CAMPOS ---

    public Omnibus(String matricula, String marca, String modelo, Integer capacidadAsientos, EstadoBus estado, Localidad localidadActual) {
        this.matricula = matricula;
        this.marca = marca;
        this.modelo = modelo;
        this.capacidadAsientos = capacidadAsientos;
        this.estado = estado;
        this.localidadActual = localidadActual;
    }
}