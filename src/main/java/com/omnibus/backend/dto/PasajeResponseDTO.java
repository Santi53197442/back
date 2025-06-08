// src/main/java/com/omnibus/backend/dto/PasajeResponseDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.EstadoPasaje;
import java.time.LocalDate;
import java.time.LocalTime;

public class PasajeResponseDTO {
    private Integer id;
    private Long clienteId;
    private String clienteNombre;
    private String clienteEmail; // <-- CAMPO AÑADIDO
    private Integer viajeId;
    private String origenViaje;
    private String destinoViaje;
    private LocalDate fechaViaje;
    private LocalTime horaSalidaViaje;
    private String omnibusMatricula; // <-- CAMPO AÑADIDO
    private Float precio;
    private EstadoPasaje estado;
    private Integer numeroAsiento;

    // Constructor por defecto
    public PasajeResponseDTO() {
    }

    // Constructor actualizado con los nuevos campos
    public PasajeResponseDTO(Integer id, Long clienteId, String clienteNombre, String clienteEmail,
                             Integer viajeId, String origenViaje, String destinoViaje,
                             LocalDate fechaViaje, LocalTime horaSalidaViaje, String omnibusMatricula,
                             Float precio, EstadoPasaje estado, Integer numeroAsiento) {
        this.id = id;
        this.clienteId = clienteId;
        this.clienteNombre = clienteNombre;
        this.clienteEmail = clienteEmail; // <-- Asignación
        this.viajeId = viajeId;
        this.origenViaje = origenViaje;
        this.destinoViaje = destinoViaje;
        this.fechaViaje = fechaViaje;
        this.horaSalidaViaje = horaSalidaViaje;
        this.omnibusMatricula = omnibusMatricula; // <-- Asignación
        this.precio = precio;
        this.estado = estado;
        this.numeroAsiento = numeroAsiento;
    }

    // --- Getters y Setters ---
    // (Los existentes se mantienen igual)

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
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
    public Float getPrecio() { return precio; }
    public void setPrecio(Float precio) { this.precio = precio; }
    public EstadoPasaje getEstado() { return estado; }
    public void setEstado(EstadoPasaje estado) { this.estado = estado; }
    public Integer getNumeroAsiento() { return numeroAsiento; }
    public void setNumeroAsiento(Integer numeroAsiento) { this.numeroAsiento = numeroAsiento; }

    // Getters y Setters para los campos nuevos
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String clienteEmail) { this.clienteEmail = clienteEmail; }
    public String getOmnibusMatricula() { return omnibusMatricula; }
    public void setOmnibusMatricula(String omnibusMatricula) { this.omnibusMatricula = omnibusMatricula; }
}