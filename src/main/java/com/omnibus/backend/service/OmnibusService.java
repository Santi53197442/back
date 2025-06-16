package com.omnibus.backend.service;

import com.omnibus.backend.dto.CreateOmnibusDTO;
import com.omnibus.backend.dto.OmnibusStatsDTO;
import com.omnibus.backend.exception.BusConViajesAsignadosException;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.LocalidadRepository;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OmnibusService {

    private static final Logger logger = LoggerFactory.getLogger(OmnibusService.class);
    private final OmnibusRepository omnibusRepository;
    private final LocalidadRepository localidadRepository;
    private final ViajeRepository viajeRepository;

    @Autowired
    public OmnibusService(OmnibusRepository omnibusRepository,
                          LocalidadRepository localidadRepository,
                          ViajeRepository viajeRepository) {
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

    @Transactional
    public Omnibus marcarOmnibusInactivo(Long omnibusId, LocalDateTime inicioInactividad, LocalDateTime finInactividad, EstadoBus nuevoEstado) {
        logger.info("Programando inactividad para ómnibus {} como {} de {} a {}", omnibusId, nuevoEstado, inicioInactividad, finInactividad);

        if (nuevoEstado != EstadoBus.EN_MANTENIMIENTO && nuevoEstado != EstadoBus.FUERA_DE_SERVICIO) {
            throw new IllegalArgumentException("El nuevo estado para inactividad debe ser EN_MANTENIMIENTO o FUERA_DE_SERVICIO.");
        }
        if (inicioInactividad.isAfter(finInactividad) || inicioInactividad.isEqual(finInactividad)) {
            throw new IllegalArgumentException("La fecha y hora de inicio de inactividad debe ser anterior a la fecha y hora de fin.");
        }

        Omnibus omnibus = omnibusRepository.findById(omnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Ómnibus no encontrado con ID: " + omnibusId));

        if (omnibus.getEstado() == EstadoBus.ASIGNADO_A_VIAJE) {
            throw new BusConViajesAsignadosException("El ómnibus está actualmente ASIGNADO_A_VIAJE y no puede marcarse inactivo directamente. Considere finalizar o reasignar sus viajes.");
        }

        List<EstadoViaje> estadosConsiderados = Arrays.asList(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO);

        List<Viaje> viajesConflictivos = viajeRepository.findOverlappingTrips(
                omnibus,
                inicioInactividad,
                finInactividad,
                estadosConsiderados
        );

        if (!viajesConflictivos.isEmpty()) {
            logger.warn("Ómnibus {} tiene {} viajes conflictivos con el periodo de inactividad.", omnibusId, viajesConflictivos.size());
            throw new BusConViajesAsignadosException(
                    "El ómnibus tiene " + viajesConflictivos.size() +
                            " viaje(s) que se solapan con el período de inactividad solicitado.",
                    viajesConflictivos
            );
        }

        logger.info("No hay conflictos. Programando inactividad para ómnibus {}.", omnibusId);
        // --- LÓGICA MODIFICADA: Se guardan los datos de la programación ---
        omnibus.setEstadoProgramado(nuevoEstado);
        omnibus.setInicioInactividadProgramada(inicioInactividad);
        omnibus.setFinInactividadProgramada(finInactividad);

        // El estado actual NO cambia.
        logger.info("Inactividad programada correctamente para el ómnibus {}. El estado actual sigue siendo {}.", omnibusId, omnibus.getEstado());
        return omnibusRepository.save(omnibus);
    }

    @Transactional
    public Omnibus marcarOmnibusOperativo(Long omnibusId) {
        Omnibus omnibus = omnibusRepository.findById(omnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Ómnibus no encontrado con ID: " + omnibusId));

        if (omnibus.getEstado() == EstadoBus.OPERATIVO) {
            logger.warn("Ómnibus {} ya está OPERATIVO.", omnibusId);
            return omnibus;
        }
        if (omnibus.getEstado() == EstadoBus.ASIGNADO_A_VIAJE) {
            throw new IllegalStateException("No se puede marcar como OPERATIVO un bus que está ASIGNADO_A_VIAJE. Debe finalizar o reasignar sus viajes primero.");
        }
        omnibus.setEstado(EstadoBus.OPERATIVO);
        // Limpiamos también los campos de programación por si acaso.
        omnibus.setEstadoProgramado(null);
        omnibus.setInicioInactividadProgramada(null);
        omnibus.setFinInactividadProgramada(null);
        logger.info("Ómnibus {} marcado como OPERATIVO.", omnibusId);
        return omnibusRepository.save(omnibus);
    }

    public List<Omnibus> obtenerOmnibusPorEstado(EstadoBus estado) {
        return omnibusRepository.findByEstado(estado);
    }

    public List<OmnibusStatsDTO> obtenerDatosParaEstadisticas() {
        List<Omnibus> omnibusLista = omnibusRepository.findAll();
        return omnibusLista.stream().map(omnibus -> new OmnibusStatsDTO(
                omnibus.getEstado(),
                omnibus.getCapacidadAsientos(),
                omnibus.getMarca(),
                omnibus.getLocalidadActual().getNombre()
        )).collect(Collectors.toList());
    }
}