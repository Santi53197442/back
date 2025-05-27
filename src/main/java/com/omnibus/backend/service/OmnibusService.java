// src/main/java/com/omnibus/backend/service/OmnibusService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.CreateOmnibusDTO;
import com.omnibus.backend.exception.BusConViajesAsignadosException; // Importar la excepción
import com.omnibus.backend.model.*; // Importar EstadoBus, EstadoViaje
import com.omnibus.backend.repository.LocalidadRepository;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository; // Importar ViajeRepository
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OmnibusService {

    private final OmnibusRepository omnibusRepository;
    private final LocalidadRepository localidadRepository;
    private final ViajeRepository viajeRepository; // Inyectar ViajeRepository

    @Autowired
    public OmnibusService(OmnibusRepository omnibusRepository,
                          LocalidadRepository localidadRepository,
                          ViajeRepository viajeRepository) { // Añadir al constructor
        this.omnibusRepository = omnibusRepository;
        this.localidadRepository = localidadRepository;
        this.viajeRepository = viajeRepository;
    }

    @Transactional
    public Omnibus crearOmnibus(CreateOmnibusDTO dto) {
        String matriculaNormalizada = dto.getMatricula().trim().toUpperCase();
        if (omnibusRepository.findByMatricula(matriculaNormalizada).isPresent()) {
            throw new IllegalArgumentException("La matrícula '" + matriculaNormalizada + "' ya está registrada.");
        }
        Localidad localidadActual = localidadRepository.findById(dto.getLocalidadActualId())
                .orElseThrow(() -> new EntityNotFoundException("Localidad con ID " + dto.getLocalidadActualId() + " no encontrada."));

        Omnibus nuevoOmnibus = new Omnibus();
        nuevoOmnibus.setMatricula(matriculaNormalizada);
        nuevoOmnibus.setMarca(dto.getMarca().trim());
        nuevoOmnibus.setModelo(dto.getModelo().trim());
        nuevoOmnibus.setCapacidadAsientos(dto.getCapacidadAsientos());
        nuevoOmnibus.setEstado(dto.getEstado());
        nuevoOmnibus.setLocalidadActual(localidadActual);

        return omnibusRepository.save(nuevoOmnibus);
    }

    public List<Omnibus> obtenerTodosLosOmnibus() {
        return omnibusRepository.findAll();
    }

    public Optional<Omnibus> obtenerOmnibusPorId(Long id) {
        return omnibusRepository.findById(id);
    }

    // --- NUEVOS MÉTODOS PARA MARCAR INACTIVO/OPERATIVO ---

    @Transactional
    public Omnibus marcarOmnibusInactivo(Long omnibusId, LocalDateTime inicioInactividad, LocalDateTime finInactividad, EstadoBus nuevoEstado) {
        if (nuevoEstado != EstadoBus.EN_MANTENIMIENTO && nuevoEstado != EstadoBus.FUERA_DE_SERVICIO) {
            throw new IllegalArgumentException("El nuevo estado para inactividad debe ser EN_MANTENIMIENTO o FUERA_DE_SERVICIO.");
        }
        if (inicioInactividad.isAfter(finInactividad) || inicioInactividad.isEqual(finInactividad)) {
            throw new IllegalArgumentException("La fecha y hora de inicio de inactividad debe ser anterior a la fecha y hora de fin.");
        }

        Omnibus omnibus = omnibusRepository.findById(omnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Ómnibus no encontrado con ID: " + omnibusId));

        if (omnibus.getEstado() == nuevoEstado) {
            // Opcional: si ya está en ese estado, se podría considerar una operación noop o lanzar error.
            // Por ahora, permitimos reafirmar el estado, pero no se realizarán cambios si es el mismo.
        }
        if (omnibus.getEstado() == EstadoBus.ASIGNADO_A_VIAJE && inicioInactividad.isBefore(LocalDateTime.now().plusMinutes(1))) {
            throw new BusConViajesAsignadosException("El ómnibus está actualmente ASIGNADO_A_VIAJE y no puede marcarse inactivo inmediatamente si la inactividad es inminente.");
        }

        List<EstadoViaje> estadosConsiderados = List.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO);
        LocalDate fechaConsultaDesde = inicioInactividad.toLocalDate().minusDays(1); // Margen para viajes que cruzan medianoche
        LocalDate fechaConsultaHasta = finInactividad.toLocalDate().plusDays(1);   // Margen para viajes que cruzan medianoche

        List<Viaje> viajesPotenciales = viajeRepository.findPotentialOverlappingTripsInDateRange(
                omnibusId, estadosConsiderados, fechaConsultaDesde, fechaConsultaHasta
        );

        List<Viaje> viajesConflictivos = new ArrayList<>();
        for (Viaje viaje : viajesPotenciales) {
            LocalDateTime inicioViaje = LocalDateTime.of(viaje.getFecha(), viaje.getHoraSalida());
            LocalDateTime finViaje;
            if (viaje.getHoraLlegada().isBefore(viaje.getHoraSalida())) { // Viaje cruza medianoche
                finViaje = LocalDateTime.of(viaje.getFecha().plusDays(1), viaje.getHoraLlegada());
            } else {
                finViaje = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());
            }

            // Lógica de solapamiento: (StartA < EndB) && (EndA > StartB)
            if (inicioViaje.isBefore(finInactividad) && finViaje.isAfter(inicioInactividad)) {
                viajesConflictivos.add(viaje);
            }
        }

        if (!viajesConflictivos.isEmpty()) {
            throw new BusConViajesAsignadosException(
                    "El ómnibus tiene " + viajesConflictivos.size() +
                            " viaje(s) (" + EstadoViaje.PROGRAMADO + "/" + EstadoViaje.EN_CURSO +
                            ") que se solapan con el período de inactividad [" + inicioInactividad + " - " + finInactividad + "].",
                    viajesConflictivos
            );
        }

        omnibus.setEstado(nuevoEstado);
        // Aquí NO guardamos el período de inactividad en el ómnibus. Solo cambiamos su estado actual.
        // La gestión de cuándo volverá a estar OPERATIVO es un proceso separado.
        return omnibusRepository.save(omnibus);
    }

    @Transactional
    public Omnibus marcarOmnibusOperativo(Long omnibusId) {
        Omnibus omnibus = omnibusRepository.findById(omnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Ómnibus no encontrado con ID: " + omnibusId));

        if (omnibus.getEstado() == EstadoBus.OPERATIVO) {
            // Ya está operativo, no hacer nada o retornar mensaje.
            return omnibus;
        }
        if (omnibus.getEstado() == EstadoBus.ASIGNADO_A_VIAJE) {
            throw new IllegalStateException("No se puede marcar como OPERATIVO un bus que está ASIGNADO_A_VIAJE. Debe finalizar o cancelar el viaje primero.");
        }
        // Aquí podrías añadir lógica para verificar si el bus tenía un "período de mantenimiento"
        // y si ya ha concluido, si tuvieras esa información almacenada.
        // Por ahora, es un cambio manual.

        omnibus.setEstado(EstadoBus.OPERATIVO);
        return omnibusRepository.save(omnibus);
    }

    public List<Omnibus> obtenerOmnibusPorEstado(EstadoBus estado) {
        return omnibusRepository.findByEstado(estado);
    }
}