package com.omnibus.backend.model; // Asegúrate que este sea tu paquete correcto

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull; // Para la anotación @NotNull
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "viaje")
@Getter
@Setter
@NoArgsConstructor // Constructor sin argumentos por Lombok
@AllArgsConstructor // Constructor con todos los argumentos por Lombok
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "La fecha del viaje no puede ser nula.") // Añadida validación, buena práctica
    @Column(nullable = false)
    private LocalDate fecha;

    @NotNull(message = "La hora de salida no puede ser nula.") // Añadida validación
    @Column(nullable = false)
    private LocalTime horaSalida;

    @NotNull(message = "La hora de llegada no puede ser nula.") // Añadida validación
    @Column(nullable = false)
    private LocalTime horaLlegada;

    @NotNull(message = "La localidad de origen no puede ser nula.") // Añadida validación
    @ManyToOne(fetch = FetchType.LAZY) // Carga perezosa es generalmente buena para el rendimiento
    @JoinColumn(name = "origen_id", nullable = false)
    private Localidad origen;

    @NotNull(message = "La localidad de destino no puede ser nula.") // Añadida validación
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destino_id", nullable = false)
    private Localidad destino;

    // Si un viaje SIEMPRE debe tener un bus asignado desde su creación, nullable=false está bien.
    // Si podría crearse y luego asignarse el bus, considera nullable=true y ajusta la lógica.
    @NotNull(message = "El bus asignado no puede ser nulo.") // Añadida validación
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_asignado_id", nullable = false)
    private Omnibus busAsignado;

    @NotNull(message = "Los asientos disponibles no pueden ser nulos.") // Añadida validación
    @Column(nullable = false)
    private Integer asientosDisponibles; // Podría ser @Min(0) si quieres asegurar no negativos

    // NUEVO CAMPO PARA EL ESTADO DEL VIAJE
    @NotNull(message = "El estado del viaje no puede ser nulo.")
    @Enumerated(EnumType.STRING) // Guarda el nombre del enum como String en la BD (ej. "PROGRAMADO")
    @Column(nullable = false)
    private EstadoViaje estado;


}