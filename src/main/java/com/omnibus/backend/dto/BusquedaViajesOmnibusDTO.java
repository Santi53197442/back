// src/main/java/com/omnibus/backend/dto/BusquedaViajesOmnibusDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoViaje;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public class BusquedaViajesOmnibusDTO {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaDesde;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaHasta;

    private EstadoViaje estadoViaje;

    private String ordenarPor; // Ej: "fecha", "horaSalida", "estado", "id"
    private String direccionOrden; // "ASC" o "DESC"

    // Getters y Setters
    public LocalDate getFechaDesde() {
        return fechaDesde;
    }

    public void setFechaDesde(LocalDate fechaDesde) {
        this.fechaDesde = fechaDesde;
    }

    public LocalDate getFechaHasta() {
        return fechaHasta;
    }

    public void setFechaHasta(LocalDate fechaHasta) {
        this.fechaHasta = fechaHasta;
    }

    public EstadoViaje getEstadoViaje() {
        return estadoViaje;
    }

    public void setEstadoViaje(EstadoViaje estadoViaje) {
        this.estadoViaje = estadoViaje;
    }

    public String getOrdenarPor() {
        return ordenarPor;
    }

    public void setOrdenarPor(String ordenarPor) {
        this.ordenarPor = ordenarPor;
    }

    public String getDireccionOrden() {
        return direccionOrden;
    }

    public void setDireccionOrden(String direccionOrden) {
        this.direccionOrden = direccionOrden;
    }

    @Override
    public String toString() {
        return "BusquedaViajesOmnibusDTO{" +
                "fechaDesde=" + fechaDesde +
                ", fechaHasta=" + fechaHasta +
                ", estadoViaje=" + estadoViaje +
                ", ordenarPor='" + ordenarPor + '\'' +
                ", direccionOrden='" + direccionOrden + '\'' +
                '}';
    }
}