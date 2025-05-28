// src/main/java/com/omnibus/backend/model/Pasaje.java
package com.omnibus.backend.model;

import jakarta.persistence.*;
import java.util.Objects; // Para hashCode e equals

@Entity
@Table(name = "pasajes") // Nombre de la tabla en la base de datos
public class Pasaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY) // Usar LAZY para no cargar el cliente a menos que se necesite
    @JoinColumn(name = "cliente_id", nullable = false) // Asume que tienes una entidad Usuario y una columna cliente_id
    private Usuario cliente;

    @Column(nullable = false)
    private Float precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPasaje estado;

    @ManyToOne(fetch = FetchType.LAZY) // Usar LAZY para no cargar el viaje a menos que se necesite
    @JoinColumn(name = "viaje_id", nullable = false) // FK a la tabla de Viaje
    private Viaje datosViaje; // Nombre del campo como en tu imagen

    // Constructores
    public Pasaje() {
    }

    public Pasaje(Usuario cliente, Float precio, EstadoPasaje estado, Viaje datosViaje) {
        this.cliente = cliente;
        this.precio = precio;
        this.estado = estado;
        this.datosViaje = datosViaje;
    }

    // Getters y Setters
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

    // Es buena práctica implementar equals y hashCode
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
                ", clienteId=" + (cliente != null ? cliente.getId() : "null") + // Evitar NRE y toString() recursivo
                ", precio=" + precio +
                ", estado=" + estado +
                ", viajeId=" + (datosViaje != null ? datosViaje.getId() : "null") + // Evitar NRE y toString() recursivo
                '}';
    }
}