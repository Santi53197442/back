package com.omnibus.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

@Entity
@Table(name = "viaje")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "La fecha y hora de salida no puede ser nula.")
    @Column(name = "fecha_hora_salida", nullable = false)
    private LocalDateTime fechaHoraSalida;

    @NotNull(message = "La fecha y hora de llegada no puede ser nula.")
    @Column(name = "fecha_hora_llegada", nullable = false)
    private LocalDateTime fechaHoraLlegada;

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
    @Min(value = 0, message = "Los asientos disponibles no pueden ser negativos.")
    @Column(nullable = false)
    private Integer asientosDisponibles;

    @Column(name = "pasajes_vendidos")
    private Integer pasajesVendidos;

    @NotNull(message = "El estado del viaje no puede ser nulo.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstadoViaje estado;

    @NotNull(message = "El precio del viaje no puede ser nulo.")
    @Positive(message = "El precio del viaje debe ser un valor positivo.")
    @Column(name = "precio", nullable = false)
    private Double precio;

    @Transient
    public LocalDate getFecha() {
        return this.fechaHoraSalida != null ? this.fechaHoraSalida.toLocalDate() : null;
    }

    @Transient
    public LocalTime getHoraSalida() {
        return this.fechaHoraSalida != null ? this.fechaHoraSalida.toLocalTime() : null;
    }

    @Transient
    public LocalTime getHoraLlegada() {
        return this.fechaHoraLlegada != null ? this.fechaHoraLlegada.toLocalTime() : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Viaje viaje = (Viaje) o;
        return Objects.equals(id, viaje.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Viaje{" +
                "id=" + id +
                ", fechaHoraSalida=" + fechaHoraSalida +
                ", fechaHoraLlegada=" + fechaHoraLlegada +
                ", origen=" + (origen != null ? origen.getId() : "null") +
                ", destino=" + (destino != null ? destino.getId() : "null") +
                ", busAsignado=" + (busAsignado != null ? busAsignado.getId() : "null") +
                ", asientosDisponibles=" + asientosDisponibles +
                ", pasajesVendidos=" + pasajesVendidos +
                ", estado=" + estado +
                ", precio=" + precio +
                '}';
    }
}