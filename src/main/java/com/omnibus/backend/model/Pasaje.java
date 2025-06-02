// src/main/java/com/omnibus/backend/model/Pasaje.java
package com.omnibus.backend.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min; // Para validar numeroAsiento
import jakarta.validation.constraints.NotNull; // Para validar numeroAsiento
import java.util.Objects;

@Entity
@Table(name = "pasajes")
public class Pasaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @NotNull(message = "El cliente no puede ser nulo para un pasaje.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @NotNull(message = "El precio del pasaje no puede ser nulo.")
    @Column(nullable = false)
    private Float precio; // Considera usar Double para consistencia con Viaje.precio

    @NotNull(message = "El estado del pasaje no puede ser nulo.")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50) // Especificar longitud
    private EstadoPasaje estado;

    @NotNull(message = "El viaje asociado al pasaje no puede ser nulo.")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viaje_id", nullable = false)
    private Viaje datosViaje;

    // --- NUEVO CAMPO PARA EL NÚMERO DE ASIENTO ---
    @NotNull(message = "El número de asiento no puede ser nulo.")
    @Min(value = 1, message = "El número de asiento debe ser al menos 1.")
    @Column(name = "numero_asiento", nullable = false)
    private Integer numeroAsiento;

    // Constructores
    public Pasaje() {
    }

    // Constructor actualizado para incluir numeroAsiento
    public Pasaje(Usuario cliente, Float precio, EstadoPasaje estado, Viaje datosViaje, Integer numeroAsiento) {
        this.cliente = cliente;
        this.precio = precio;
        this.estado = estado;
        this.datosViaje = datosViaje;
        this.numeroAsiento = numeroAsiento;
    }

    // Getters y Setters (Lombok también podría generar estos si añades @Getter @Setter a la clase)

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    public Float getPrecio() {
        return precio;
    }

    public void setPrecio(Float precio) {
        this.precio = precio;
    }

    public EstadoPasaje getEstado() {
        return estado;
    }

    public void setEstado(EstadoPasaje estado) {
        this.estado = estado;
    }

    public Viaje getDatosViaje() {
        return datosViaje;
    }

    public void setDatosViaje(Viaje datosViaje) {
        this.datosViaje = datosViaje;
    }

    // --- GETTER Y SETTER PARA numeroAsiento ---
    public Integer getNumeroAsiento() {
        return numeroAsiento;
    }

    public void setNumeroAsiento(Integer numeroAsiento) {
        this.numeroAsiento = numeroAsiento;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pasaje pasaje = (Pasaje) o;
        return Objects.equals(id, pasaje.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Pasaje{" +
                "id=" + id +
                ", clienteId=" + (cliente != null ? cliente.getId() : "null") +
                ", precio=" + precio +
                ", estado=" + estado +
                ", viajeId=" + (datosViaje != null ? datosViaje.getId() : "null") +
                ", numeroAsiento=" + numeroAsiento + // Añadido al toString
                '}';
    }
}