// src/main/java/com/omnibus/backend/dto/BusquedaViajesGeneralDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoViaje; // Asegúrate que la importación sea correcta
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

public class BusquedaViajesGeneralDTO {

    private Long origenId;
    private Long destinoId;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaDesde; // Fecha de inicio para el rango de búsqueda

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fechaHasta; // Fecha de fin para el rango de búsqueda

    private EstadoViaje estado; // Filtrar por estado del viaje (PENDIENTE, EN_CURSO, FINALIZADO, CANCELADO)

    @Min(value = 0, message = "El mínimo de asientos disponibles no puede ser negativo")
    private Integer minAsientosDisponibles; // Filtrar por un mínimo de asientos disponibles

    private String sortBy; // Campo por el cual ordenar (ej: "fechaSalida", "origen.nombre", "asientosDisponibles")
    private String sortDir; // Dirección del ordenamiento ("asc" o "desc")

    // Getters y Setters

    public Long getOrigenId() {
        return origenId;
    }

    public void setOrigenId(Long origenId) {
        this.origenId = origenId;
    }

    public Long getDestinoId() {
        return destinoId;
    }

    public void setDestinoId(Long destinoId) {
        this.destinoId = destinoId;
    }

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

    public EstadoViaje getEstado() {
        return estado;
    }

    public void setEstado(EstadoViaje estado) {
        this.estado = estado;
    }

    public Integer getMinAsientosDisponibles() {
        return minAsientosDisponibles;
    }

    public void setMinAsientosDisponibles(Integer minAsientosDisponibles) {
        this.minAsientosDisponibles = minAsientosDisponibles;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getSortDir() {
        return sortDir;
    }

    public void setSortDir(String sortDir) {
        this.sortDir = sortDir;
    }

    @Override
    public String toString() {
        return "BusquedaViajesGeneralDTO{" +
                "origenId=" + origenId +
                ", destinoId=" + destinoId +
                ", fechaDesde=" + fechaDesde +
                ", fechaHasta=" + fechaHasta +
                ", estado=" + estado +
                ", minAsientosDisponibles=" + minAsientosDisponibles +
                ", sortBy='" + sortBy + '\'' +
                ", sortDir='" + sortDir + '\'' +
                '}';
    }
}