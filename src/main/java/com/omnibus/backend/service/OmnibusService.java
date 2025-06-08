// src/main/java/com/omnibus/backend/service/OmnibusService.java
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
        logger.info("Intentando marcar ómnibus {} como {} de {} a {}", omnibusId, nuevoEstado, inicioInactividad, finInactividad);
        if (nuevoEstado != EstadoBus.EN_MANTENIMIENTO && nuevoEstado != EstadoBus.FUERA_DE_SERVICIO) {
            throw new IllegalArgumentException("El nuevo estado para inactividad debe ser EN_MANTENIMIENTO o FUERA_DE_SERVICIO.");
        }
        if (inicioInactividad.isAfter(finInactividad) || inicioInactividad.isEqual(finInactividad)) {
            throw new IllegalArgumentException("La fecha y hora de inicio de inactividad debe ser anterior a la fecha y hora de fin.");
        }

        Omnibus omnibus = omnibusRepository.findById(omnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Ómnibus no encontrado con ID: " + omnibusId));

        if (omnibus.getEstado() == nuevoEstado) {
            logger.warn("Ómnibus {} ya está en estado {}. No se realizan cambios.", omnibusId, nuevoEstado);
            return omnibus;
        }
        if (omnibus.getEstado() == EstadoBus.ASIGNADO_A_VIAJE) {
            throw new BusConViajesAsignadosException("El ómnibus está actualmente ASIGNADO_A_VIAJE y no puede marcarse inactivo directamente. Considere finalizar o reasignar sus viajes.");
        }

        List<EstadoViaje> estadosConsiderados = List.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO);
        LocalDate fechaConsultaDesde = inicioInactividad.toLocalDate();
        LocalDate fechaConsultaHasta = finInactividad.toLocalDate();

        List<Viaje> viajesPotenciales = viajeRepository.findPotentialOverlappingTripsInDateRange(
                omnibusId, estadosConsiderados, fechaConsultaDesde, fechaConsultaHasta
        );
        logger.debug("Encontrados {} viajes potenciales para ómnibus {} en el rango de fechas {} a {}",
                viajesPotenciales.size(), omnibusId, fechaConsultaDesde, fechaConsultaHasta);

        List<Viaje> viajesConflictivos = new ArrayList<>();
        for (Viaje viaje : viajesPotenciales) {
            LocalDateTime inicioViaje = LocalDateTime.of(viaje.getFecha(), viaje.getHoraSalida());
            LocalDateTime finViaje;
            if (viaje.getHoraLlegada().isBefore(viaje.getHoraSalida())) {
                finViaje = LocalDateTime.of(viaje.getFecha().plusDays(1), viaje.getHoraLlegada());
            } else {
                finViaje = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());
            }
            if (inicioViaje.isBefore(finInactividad) && finViaje.isAfter(inicioInactividad)) {
                logger.debug("Conflicto detectado: Viaje ID {} ({}-{}) con periodo de inactividad ({}-{})",
                        viaje.getId(), inicioViaje, finViaje, inicioInactividad, finInactividad);
                viajesConflictivos.add(viaje);
            }
        }

        if (!viajesConflictivos.isEmpty()) {
            logger.warn("Ómnibus {} tiene {} viajes conflictivos con el periodo de inactividad.", omnibusId, viajesConflictivos.size());
            throw new BusConViajesAsignadosException(
                    "El ómnibus tiene " + viajesConflictivos.size() +
                            " viaje(s) (" + EstadoViaje.PROGRAMADO.name() + "/" + EstadoViaje.EN_CURSO.name() +
                            ") que se solapan con el período de inactividad solicitado [" + inicioInactividad + " - " + finInactividad + "].",
                    viajesConflictivos
            );
        }

        logger.info("No hay conflictos. Marcando ómnibus {} como {}.", omnibusId, nuevoEstado);
        omnibus.setEstado(nuevoEstado);
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
        logger.info("Ómnibus {} marcado como OPERATIVO.", omnibusId);
        return omnibusRepository.save(omnibus);
    }

    public List<Omnibus> obtenerOmnibusPorEstado(EstadoBus estado) {
        return omnibusRepository.findByEstado(estado);
    }


    public List<OmnibusStatsDTO> obtenerDatosParaEstadisticas() {
        // 1. Usar una consulta optimizada si es posible, si no, findAll() está bien para flotas moderadas.
        List<Omnibus> omnibusLista = omnibusRepository.findAll();

        // 2. Mapear cada entidad Omnibus a un OmnibusStatsDTO
        return omnibusLista.stream().map(omnibus -> new OmnibusStatsDTO(
                omnibus.getEstado(),
                omnibus.getCapacidadAsientos(),
                omnibus.getMarca(),
                omnibus.getLocalidadActual().getNombre() // Obtenemos el nombre de la localidad
        )).collect(Collectors.toList());
    }
}