package com.omnibus.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive; // Para el precio
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder; // Si quieres usar el patrón Builder

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects; // Para equals y hashCode

@Entity
@Table(name = "viaje") // Si tu tabla se llama 'viaje', si no, ajusta a 'viajes'
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder // Opcional, pero útil para crear instancias
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "La fecha del viaje no puede ser nula.")
    @Column(nullable = false)
    private LocalDate fecha;

    @NotNull(message = "La hora de salida no puede ser nula.")
    @Column(nullable = false)
    private LocalTime horaSalida;

    @NotNull(message = "La hora de llegada no puede ser nula.")
    @Column(nullable = false)
    private LocalTime horaLlegada;

    @NotNull(message = "La localidad de origen no puede ser nula.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "origen_id", nullable = false)
    private Localidad origen;

    @NotNull(message = "La localidad de destino no puede ser nula.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destino_id", nullable = false)
    private Localidad destino;

    @NotNull(message = "El bus asignado no puede ser nulo.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bus_asignado_id", nullable = false)
    private Omnibus busAsignado;

    @NotNull(message = "Los asientos disponibles no pueden ser nulos.")
    @Min(value = 0, message = "Los asientos disponibles no pueden ser negativos.") // Buena validación
    @Column(nullable = false)
    private Integer asientosDisponibles;

    @NotNull(message = "El estado del viaje no puede ser nulo.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) // Especificar longitud para el String del Enum
    private EstadoViaje estado;

    // --- NUEVO CAMPO PRECIO ---
    @NotNull(message = "El precio del viaje no puede ser nulo.")
    @Positive(message = "El precio del viaje debe ser un valor positivo.")
    @Column(name = "precio", nullable = false) // Nombre de columna y no nulo
    private Double precio;

    // Lombok genera getters y setters, constructores.
    // Es buena práctica sobreescribir equals y hashCode si la entidad se usa en colecciones (Set, Map)
    // o si se compara por identidad de negocio.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Viaje viaje = (Viaje) o;
        return Objects.equals(id, viaje.id); // Compara solo por ID para entidades JPA
    }

    @Override
    public int hashCode() {
        return Objects.hash(id); // Usa solo el ID para el hashCode
    }

    @Override
    public String toString() {
        return "Viaje{" +
                "id=" + id +
                ", fecha=" + fecha +
                ", horaSalida=" + horaSalida +
                ", horaLlegada=" + horaLlegada +
                ", origen=" + (origen != null ? origen.getId() : "null") +
                ", destino=" + (destino != null ? destino.getId() : "null") +
                ", busAsignado=" + (busAsignado != null ? busAsignado.getId() : "null") +
                ", asientosDisponibles=" + asientosDisponibles +
                ", estado=" + estado +
                ", precio=" + precio +
                '}';
    }
}