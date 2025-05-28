// src/main/java/com/omnibus/backend/dto/ViajeConDisponibilidadDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoViaje;
import java.time.LocalDateTime; // Asumiendo que tus fechas de viaje usan LocalDateTime

public class ViajeConDisponibilidadDTO {
    private Integer id;
    private LocalDateTime fechaSalida; // O el tipo de dato que uses para la fecha del viaje
    private LocalDateTime fechaLlegada; // O el tipo de dato que uses para la fecha del viaje
    private String origenNombre;
    private String destinoNombre;
    private String omnibusMatricula;
    private int capacidadOmnibus;
    private int asientosVendidos;
    private int asientosDisponibles;
    private EstadoViaje estado;
    private Double precio; // Si tienes precio por viaje

    // Constructor, Getters y Setters

    public ViajeConDisponibilidadDTO() {}

    public ViajeConDisponibilidadDTO(Integer id, LocalDateTime fechaSalida, LocalDateTime fechaLlegada, String origenNombre, String destinoNombre, String omnibusMatricula, int capacidadOmnibus, int asientosVendidos, EstadoViaje estado, Double precio) {
        this.id = id;
        this.fechaSalida = fechaSalida;
        this.fechaLlegada = fechaLlegada;
        this.origenNombre = origenNombre;
        this.destinoNombre = destinoNombre;
        this.omnibusMatricula = omnibusMatricula;
        this.capacidadOmnibus = capacidadOmnibus;
        this.asientosVendidos = asientosVendidos;
        this.asientosDisponibles = capacidadOmnibus - asientosVendidos;
        this.estado = estado;
        this.precio = precio;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public LocalDateTime getFechaSalida() {
        return fechaSalida;
    }

    public void setFechaSalida(LocalDateTime fechaSalida) {
        this.fechaSalida = fechaSalida;
    }

    public LocalDateTime getFechaLlegada() {
        return fechaLlegada;
    }

    public void setFechaLlegada(LocalDateTime fechaLlegada) {
        this.fechaLlegada = fechaLlegada;
    }

    public String getOrigenNombre() {
        return origenNombre;
    }

    public void setOrigenNombre(String origenNombre) {
        this.origenNombre = origenNombre;
    }

    public String getDestinoNombre() {
        return destinoNombre;
    }

    public void setDestinoNombre(String destinoNombre) {
        this.destinoNombre = destinoNombre;
    }

    public String getOmnibusMatricula() {
        return omnibusMatricula;
    }

    public void setOmnibusMatricula(String omnibusMatricula) {
        this.omnibusMatricula = omnibusMatricula;
    }

    public int getCapacidadOmnibus() {
        return capacidadOmnibus;
    }

    public void setCapacidadOmnibus(int capacidadOmnibus) {
        this.capacidadOmnibus = capacidadOmnibus;
        // Recalcular disponibles si la capacidad cambia
        if (this.asientosVendidos >= 0) {
            this.asientosDisponibles = this.capacidadOmnibus - this.asientosVendidos;
        }
    }

    public int getAsientosVendidos() {
        return asientosVendidos;
    }

    public void setAsientosVendidos(int asientosVendidos) {
        this.asientosVendidos = asientosVendidos;
        // Recalcular disponibles si los vendidos cambian
        if (this.capacidadOmnibus >= 0) {
            this.asientosDisponibles = this.capacidadOmnibus - this.asientosVendidos;
        }
    }

    public int getAsientosDisponibles() {
        return asientosDisponibles;
    }

    public void setAsientosDisponibles(int asientosDisponibles) {
        this.asientosDisponibles = asientosDisponibles;
    }

    public EstadoViaje getEstado() {
        return estado;
    }

    public void setEstado(EstadoViaje estado) {
        this.estado = estado;
    }

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }
}