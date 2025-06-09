// src/main/java/com/omnibus/backend/dto/PasajeResponseDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoPasaje;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;

public class PasajeResponseDTO {
    private Integer id;
    private Long clienteId;
    private String clienteNombre;
    private String clienteEmail;
    private Integer viajeId;
    private String origenViaje; // Nota: en PasajeService usamos origenNombre y destinoNombre
    private String destinoViaje; // Es buena idea mantener los nombres consistentes
    private LocalDate fechaViaje;
    private LocalTime horaSalidaViaje;
    private String omnibusMatricula;
    private Double precio; // Cambiado a Double para consistencia con la entidad Pasaje
    private EstadoPasaje estado;
    private Integer numeroAsiento;

    // --- CAMPO NUEVO DECLARADO ---
    private LocalDateTime fechaReserva;

    // Constructor por defecto
    public PasajeResponseDTO() {
    }

    // ====================== CONSTRUCTOR CORREGIDO ======================
    // Ahora acepta 14 parámetros, incluyendo fechaReserva
    public PasajeResponseDTO(Integer id, Long clienteId, String clienteNombre, String clienteEmail,
                             Integer viajeId, String origenViaje, String destinoViaje,
                             LocalDate fechaViaje, LocalTime horaSalidaViaje, String omnibusMatricula,
                             Double precio, EstadoPasaje estado, Integer numeroAsiento,
                             LocalDateTime fechaReserva) { // <-- Parámetro añadido
        this.id = id;
        this.clienteId = clienteId;
        this.clienteNombre = clienteNombre;
        this.clienteEmail = clienteEmail;
        this.viajeId = viajeId;
        this.origenViaje = origenViaje;
        this.destinoViaje = destinoViaje;
        this.fechaViaje = fechaViaje;
        this.horaSalidaViaje = horaSalidaViaje;
        this.omnibusMatricula = omnibusMatricula;
        this.precio = precio;
        this.estado = estado;
        this.numeroAsiento = numeroAsiento;
        this.fechaReserva = fechaReserva; // <-- Asignación añadida
    }

    // --- Getters y Setters ---
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public Integer getViajeId() { return viajeId; }
    public void setViajeId(Integer viajeId) { this.viajeId = viajeId; }
    public String getOrigenViaje() { return origenViaje; }
    public void setOrigenViaje(String origenViaje) { this.origenViaje = origenViaje; }
    public String getDestinoViaje() { return destinoViaje; }
    public void setDestinoViaje(String destinoViaje) { this.destinoViaje = destinoViaje; }
    public LocalDate getFechaViaje() { return fechaViaje; }
    public void setFechaViaje(LocalDate fechaViaje) { this.fechaViaje = fechaViaje; }
    public LocalTime getHoraSalidaViaje() { return horaSalidaViaje; }
    public void setHoraSalidaViaje(LocalTime horaSalidaViaje) { this.horaSalidaViaje = horaSalidaViaje; }
    public Double getPrecio() { return precio; }
    public void setPrecio(Double precio) { this.precio = precio; }
    public EstadoPasaje getEstado() { return estado; }
    public void setEstado(EstadoPasaje estado) { this.estado = estado; }
    public Integer getNumeroAsiento() { return numeroAsiento; }
    public void setNumeroAsiento(Integer numeroAsiento) { this.numeroAsiento = numeroAsiento; }
    public String getOmnibusMatricula() { return omnibusMatricula; }
    public void setOmnibusMatricula(String omnibusMatricula) { this.omnibusMatricula = omnibusMatricula; }

    // ====================== GETTER Y SETTER AÑADIDOS ======================
    public LocalDateTime getFechaReserva() {
        return fechaReserva;
    }

    public void setFechaReserva(LocalDateTime fechaReserva) {
        this.fechaReserva = fechaReserva;
    }
    // ===================================================================
}