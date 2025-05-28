// src/main/java/com/omnibus/backend/service/ViajeService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.BusquedaViajesOmnibusDTO;
import com.omnibus.backend.dto.ViajeRequestDTO;
import com.omnibus.backend.dto.ViajeResponseDTO;
import com.omnibus.backend.model.*;
// Si usas el metamodelo JPA (opcional pero recomendado para type-safety en Specifications):
// import com.omnibus.backend.model.Viaje_;
// import com.omnibus.backend.model.Omnibus_;
import com.omnibus.backend.repository.LocalidadRepository;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ViajeService {

    private static final Logger logger = LoggerFactory.getLogger(ViajeService.class);

    private static final Duration MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC = Duration.ofHours(12);
    private static final Duration MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES = Duration.ofHours(2);
    private static final Duration MIN_BUFFER_OPERATIVO_POST_LLEGADA = Duration.ofMinutes(30);

    private final ViajeRepository viajeRepository;
    private final LocalidadRepository localidadRepository;
    private final OmnibusRepository omnibusRepository;

    @Autowired
    public ViajeService(ViajeRepository viajeRepository,
                        LocalidadRepository localidadRepository,
                        OmnibusRepository omnibusRepository) {
        this.viajeRepository = viajeRepository;
        this.localidadRepository = localidadRepository;
        this.omnibusRepository = omnibusRepository;
    }

    @Transactional
    public ViajeResponseDTO crearViaje(ViajeRequestDTO requestDTO) {
        logger.info("Iniciando proceso de creación de viaje: {}", requestDTO);
        if (requestDTO.getHoraSalida().isAfter(requestDTO.getHoraLlegada()) ||
                requestDTO.getHoraSalida().equals(requestDTO.getHoraLlegada())) {
            throw new IllegalArgumentException("La hora de salida debe ser anterior a la hora de llegada.");
        }
        if (requestDTO.getOrigenId().equals(requestDTO.getDestinoId())) {
            throw new IllegalArgumentException("La localidad de origen y destino no pueden ser la misma.");
        }

        Localidad origenNuevoViaje = localidadRepository.findById(requestDTO.getOrigenId())
                .orElseThrow(() -> new EntityNotFoundException("Localidad de origen no encontrada con ID: " + requestDTO.getOrigenId()));
        Localidad destinoNuevoViaje = localidadRepository.findById(requestDTO.getDestinoId())
                .orElseThrow(() -> new EntityNotFoundException("Localidad de destino no encontrada con ID: " + requestDTO.getDestinoId()));

        LocalDateTime salidaNuevoViajeDT = LocalDateTime.of(requestDTO.getFecha(), requestDTO.getHoraSalida());
        LocalDateTime llegadaNuevoViajeDT = LocalDateTime.of(requestDTO.getFecha(), requestDTO.getHoraLlegada());

        List<Omnibus> busesPotenciales = omnibusRepository.findByEstado(EstadoBus.OPERATIVO);
        if (busesPotenciales.isEmpty()){
            throw new NoBusDisponibleException("No hay ómnibus en estado OPERATIVO en el sistema.");
        }
        Omnibus busSeleccionado = null;
        for (Omnibus busCandidato : busesPotenciales) {
            logger.debug("Evaluando bus candidato para NUEVO VIAJE: {}", busCandidato.getMatricula());
            List<Viaje> viajesConflictivosDirectos = viajeRepository
                    .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoIn(
                            busCandidato,
                            requestDTO.getFecha(),
                            requestDTO.getHoraLlegada(),
                            requestDTO.getHoraSalida(),
                            List.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO)
                    );
            if (!viajesConflictivosDirectos.isEmpty()) {
                logger.debug("Bus {} tiene conflicto directo para el nuevo viaje.", busCandidato.getMatricula());
                continue;
            }
            Localidad ubicacionPrevistaDelBusParaNuevoViaje = busCandidato.getLocalidadActual();
            LocalDateTime horaLlegadaUltimoViajeDT = null;
            List<Viaje> ultimosViajesActivosList = viajeRepository.findUltimoViajeActivo(
                    busCandidato, requestDTO.getFecha(), requestDTO.getHoraSalida());
            if (!ultimosViajesActivosList.isEmpty()) {
                Viaje ultimoViajeActivoBus = ultimosViajesActivosList.get(0);
                ubicacionPrevistaDelBusParaNuevoViaje = ultimoViajeActivoBus.getDestino();
                horaLlegadaUltimoViajeDT = LocalDateTime.of(ultimoViajeActivoBus.getFecha(), ultimoViajeActivoBus.getHoraLlegada());
            }
            if (!ubicacionPrevistaDelBusParaNuevoViaje.getId().equals(origenNuevoViaje.getId())) {
                logger.debug("Bus {} no estará en la localidad de origen {} para el nuevo viaje. Estará en {}", busCandidato.getMatricula(), origenNuevoViaje.getNombre(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre());
                continue;
            }
            if (horaLlegadaUltimoViajeDT != null) {
                if (horaLlegadaUltimoViajeDT.plus(MIN_BUFFER_OPERATIVO_POST_LLEGADA).isAfter(salidaNuevoViajeDT)) {
                    logger.debug("Bus {} no tiene suficiente tiempo de preparación en origen. Llega a las {} (+{} min buffer) vs salida nuevo viaje {}",
                            busCandidato.getMatricula(), horaLlegadaUltimoViajeDT, MIN_BUFFER_OPERATIVO_POST_LLEGADA.toMinutes(), salidaNuevoViajeDT);
                    continue;
                }
            }
            List<Viaje> proximosViajesProgramadosList = viajeRepository.findProximoViajeProgramado(
                    busCandidato, requestDTO.getFecha(), requestDTO.getHoraLlegada());
            if (!proximosViajesProgramadosList.isEmpty()) {
                Viaje proximoViajeAsignado = proximosViajesProgramadosList.get(0);
                LocalDateTime salidaProximoViajeAsignadoDT = LocalDateTime.of(proximoViajeAsignado.getFecha(), proximoViajeAsignado.getHoraSalida());
                if (destinoNuevoViaje.getId().equals(proximoViajeAsignado.getOrigen().getId())) {
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES).isAfter(salidaProximoViajeAsignadoDT)) {
                        continue;
                    }
                } else {
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC).isAfter(salidaProximoViajeAsignadoDT)) {
                        continue;
                    }
                }
            }
            busSeleccionado = busCandidato;
            break;
        }
        if (busSeleccionado == null) {
            throw new NoBusDisponibleException("No hay ómnibus disponibles que cumplan todos los criterios.");
        }
        Viaje nuevoViaje = new Viaje();
        nuevoViaje.setFecha(requestDTO.getFecha());
        nuevoViaje.setHoraSalida(requestDTO.getHoraSalida());
        nuevoViaje.setHoraLlegada(requestDTO.getHoraLlegada());
        nuevoViaje.setOrigen(origenNuevoViaje);
        nuevoViaje.setDestino(destinoNuevoViaje);
        nuevoViaje.setBusAsignado(busSeleccionado);
        nuevoViaje.setAsientosDisponibles(busSeleccionado.getCapacidadAsientos());
        nuevoViaje.setEstado(EstadoViaje.PROGRAMADO);
        // nuevoViaje.setCapacidadConfigurada(busSeleccionado.getCapacidadAsientos()); // Si añades este campo
        busSeleccionado.setEstado(EstadoBus.ASIGNADO_A_VIAJE);
        omnibusRepository.save(busSeleccionado);
        Viaje viajeGuardado = viajeRepository.save(nuevoViaje);
        logger.info("Viaje creado ID: {}. Bus asignado: {}", viajeGuardado.getId(), busSeleccionado.getMatricula());
        return mapToViajeResponseDTO(viajeGuardado);
    }

    @Transactional
    public void finalizarViaje(Integer viajeId) {
        logger.info("Intentando finalizar viaje con ID: {}", viajeId);
        Viaje viaje = viajeRepository.findById(viajeId)
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId));
        if (viaje.getEstado() == EstadoViaje.FINALIZADO) {
            logger.warn("Viaje {} ya estaba finalizado.", viajeId);
            return;
        }
        if (viaje.getEstado() == EstadoViaje.CANCELADO) {
            throw new IllegalStateException("No se puede finalizar un viaje cancelado.");
        }
        Omnibus bus = viaje.getBusAsignado();
        if (bus == null) {
            throw new IllegalStateException("El viaje " + viajeId + " no tiene bus asignado.");
        }
        viaje.setEstado(EstadoViaje.FINALIZADO);
        viajeRepository.save(viaje);
        Localidad destinoViaje = viaje.getDestino();
        bus.setLocalidadActual(destinoViaje);
        bus.setEstado(EstadoBus.OPERATIVO);
        omnibusRepository.save(bus);
        logger.info("Viaje {} finalizado. Bus {} (ID: {}) ahora en {} y OPERATIVO.",
                viajeId, bus.getMatricula(), bus.getId(), destinoViaje.getNombre());
    }

    @Transactional
    public ViajeResponseDTO reasignarViaje(Integer viajeId, Long nuevoOmnibusId)
            throws EntityNotFoundException, IllegalArgumentException, IllegalStateException, NoBusDisponibleException {
        logger.info("Iniciando reasignación del viaje ID {} al ómnibus ID {}", viajeId, nuevoOmnibusId);
        Viaje viaje = viajeRepository.findById(viajeId)
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId));
        Omnibus nuevoOmnibus = omnibusRepository.findById(nuevoOmnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Nuevo ómnibus no encontrado con ID: " + nuevoOmnibusId));
        Omnibus omnibusAnterior = viaje.getBusAsignado();
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new IllegalStateException("El viaje con ID " + viajeId + " no está PROGRAMADO. Estado: " + viaje.getEstado());
        }
        if (omnibusAnterior != null && omnibusAnterior.getId().equals(nuevoOmnibusId)) {
            throw new IllegalArgumentException("El nuevo ómnibus es el mismo que el actualmente asignado.");
        }
        if (nuevoOmnibus.getEstado() != EstadoBus.OPERATIVO) {
            throw new IllegalArgumentException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no está OPERATIVO. Estado: " + nuevoOmnibus.getEstado());
        }
        if (!nuevoOmnibus.getLocalidadActual().getId().equals(viaje.getOrigen().getId())) {
            throw new IllegalArgumentException("El nuevo ómnibus no se encuentra en la localidad de origen del viaje (" + viaje.getOrigen().getNombre() + ").");
        }
        int pasajesVendidos = 0;
        if (omnibusAnterior != null) {
            // Asumiendo que Viaje no tiene capacidadConfigurada, usamos la del bus anterior
            pasajesVendidos = omnibusAnterior.getCapacidadAsientos() - viaje.getAsientosDisponibles();
        } else {
            logger.warn("Viaje {} (PROGRAMADO) no tenía bus anterior. Asumiendo 0 pasajes vendidos.", viajeId);
        }
        if (pasajesVendidos < 0) pasajesVendidos = 0; // Corrección
        if (nuevoOmnibus.getCapacidadAsientos() < pasajesVendidos) {
            throw new IllegalArgumentException("El nuevo ómnibus no tiene suficientes asientos. Vendidos: " + pasajesVendidos + ", Capacidad nuevo: " + nuevoOmnibus.getCapacidadAsientos());
        }
        LocalDateTime salidaViajeDT = LocalDateTime.of(viaje.getFecha(), viaje.getHoraSalida());
        LocalDateTime llegadaViajeDT = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());
        List<Viaje> viajesConflictivosDirectos = viajeRepository
                .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoInAndIdNot(
                        nuevoOmnibus, viaje.getFecha(), viaje.getHoraLlegada(), viaje.getHoraSalida(),
                        List.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO), viaje.getId()
                );
        if (!viajesConflictivosDirectos.isEmpty()) {
            throw new NoBusDisponibleException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") tiene conflicto horario directo con otro viaje (ID: " + viajesConflictivosDirectos.get(0).getId() + ").");
        }
        Localidad ubicacionPrevistaNuevoOmnibus = nuevoOmnibus.getLocalidadActual();
        LocalDateTime horaLlegadaUltimoViajeNuevoOmnibusDT = null;
        List<Viaje> ultimosViajesActivosNuevoOmnibusList = viajeRepository.findUltimoViajeActivo(
                nuevoOmnibus, viaje.getFecha(), viaje.getHoraSalida());
        if (!ultimosViajesActivosNuevoOmnibusList.isEmpty()) {
            Viaje ultimoViaje = ultimosViajesActivosNuevoOmnibusList.get(0);
            if (!ultimoViaje.getId().equals(viaje.getId())) {
                ubicacionPrevistaNuevoOmnibus = ultimoViaje.getDestino();
                horaLlegadaUltimoViajeNuevoOmnibusDT = LocalDateTime.of(ultimoViaje.getFecha(), ultimoViaje.getHoraLlegada());
            }
        }
        if (!ubicacionPrevistaNuevoOmnibus.getId().equals(viaje.getOrigen().getId())) {
            throw new NoBusDisponibleException("El nuevo ómnibus no estará en la localidad de origen a tiempo.");
        }
        if (horaLlegadaUltimoViajeNuevoOmnibusDT != null && horaLlegadaUltimoViajeNuevoOmnibusDT.plus(MIN_BUFFER_OPERATIVO_POST_LLEGADA).isAfter(salidaViajeDT)) {
            throw new NoBusDisponibleException("El nuevo ómnibus no tiene suficiente tiempo de preparación.");
        }
        List<Viaje> proximosViajesProgramadosNuevoOmnibusList = viajeRepository.findProximoViajeProgramado(
                nuevoOmnibus, viaje.getFecha(), viaje.getHoraLlegada());
        if (!proximosViajesProgramadosNuevoOmnibusList.isEmpty()) {
            Viaje proximoViaje = proximosViajesProgramadosNuevoOmnibusList.get(0);
            if (!proximoViaje.getId().equals(viaje.getId())) {
                LocalDateTime salidaProximoViajeAsignadoNuevoOmnibusDT = LocalDateTime.of(proximoViaje.getFecha(), proximoViaje.getHoraSalida());
                if (viaje.getDestino().getId().equals(proximoViaje.getOrigen().getId())) {
                    if (llegadaViajeDT.plus(MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES).isAfter(salidaProximoViajeAsignadoNuevoOmnibusDT)) {
                        throw new NoBusDisponibleException("Conflicto buffer (misma loc) con próximo viaje del nuevo ómnibus.");
                    }
                } else {
                    if (llegadaViajeDT.plus(MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC).isAfter(salidaProximoViajeAsignadoNuevoOmnibusDT)) {
                        throw new NoBusDisponibleException("Conflicto buffer (diferente loc) con próximo viaje del nuevo ómnibus.");
                    }
                }
            }
        }
        if (omnibusAnterior != null) {
            omnibusAnterior.setEstado(EstadoBus.OPERATIVO);
            omnibusRepository.save(omnibusAnterior);
        }
        viaje.setBusAsignado(nuevoOmnibus);
        // viaje.setCapacidadConfigurada(nuevoOmnibus.getCapacidadAsientos()); // Si usas este campo
        viaje.setAsientosDisponibles(nuevoOmnibus.getCapacidadAsientos() - pasajesVendidos);
        nuevoOmnibus.setEstado(EstadoBus.ASIGNADO_A_VIAJE);
        omnibusRepository.save(nuevoOmnibus);
        Viaje viajeActualizado = viajeRepository.save(viaje);
        logger.info("Viaje ID {} reasignado a ómnibus ID {}.", viajeId, nuevoOmnibusId);
        return mapToViajeResponseDTO(viajeActualizado);
    }

    private ViajeResponseDTO mapToViajeResponseDTO(Viaje viaje) {
        ViajeResponseDTO.ViajeResponseDTOBuilder builder = ViajeResponseDTO.builder()
                .id(viaje.getId())
                .fecha(viaje.getFecha())
                .horaSalida(viaje.getHoraSalida())
                .horaLlegada(viaje.getHoraLlegada())
                .origenId(viaje.getOrigen().getId())
                .origenNombre(viaje.getOrigen().getNombre())
                .destinoId(viaje.getDestino().getId())
                .destinoNombre(viaje.getDestino().getNombre())
                .asientosDisponibles(viaje.getAsientosDisponibles());

        if (viaje.getBusAsignado() != null) {
            builder.busAsignadoId(viaje.getBusAsignado().getId());
            builder.busMatricula(viaje.getBusAsignado().getMatricula());
            // builder.capacidadTotal(viaje.getBusAsignado().getCapacidadAsientos()); // Opcional
        } else {
            builder.busMatricula("N/A");
        }

        if (viaje.getEstado() != null) {
            builder.estado(viaje.getEstado().name());
        }
        return builder.build();
    }

    public List<ViajeResponseDTO> obtenerViajesPorEstado(EstadoViaje estado) {
        logger.info("Buscando viajes con estado: {}", estado);
        List<Viaje> viajesEncontrados = viajeRepository.findByEstado(estado);
        if (viajesEncontrados.isEmpty()) {
            logger.info("No se encontraron viajes para el estado: {}", estado);
            return new ArrayList<>();
        }
        logger.info("Se encontraron {} viajes para el estado: {}", viajesEncontrados.size(), estado);
        return viajesEncontrados.stream()
                .map(this::mapToViajeResponseDTO)
                .collect(Collectors.toList());
    }

    public List<ViajeResponseDTO> buscarViajesDeOmnibus(Long omnibusId, BusquedaViajesOmnibusDTO dto) {
        logger.info("Buscando viajes para ómnibus ID {} con criterios: {}", omnibusId, dto);
        omnibusRepository.findById(omnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Ómnibus no encontrado con ID: " + omnibusId));

        Specification<Viaje> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("busAsignado").get("id"), omnibusId));

            if (dto.getFechaDesde() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fecha"), dto.getFechaDesde()));
            }
            if (dto.getFechaHasta() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("fecha"), dto.getFechaHasta()));
            }
            if (dto.getEstadoViaje() != null) {
                predicates.add(criteriaBuilder.equal(root.get("estado"), dto.getEstadoViaje()));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.unsorted();
        if (dto.getOrdenarPor() != null && !dto.getOrdenarPor().trim().isEmpty()) {
            String campoOrden = dto.getOrdenarPor().trim();
            if (List.of("fecha", "horaSalida", "estado", "id").contains(campoOrden)) {
                Sort.Direction direction = Sort.Direction.ASC;
                if (dto.getDireccionOrden() != null && "DESC".equalsIgnoreCase(dto.getDireccionOrden().trim())) {
                    direction = Sort.Direction.DESC;
                }
                sort = Sort.by(direction, campoOrden);
            } else {
                logger.warn("Campo de ordenamiento no válido: {}. Usando orden por defecto (fecha, horaSalida ASC).", campoOrden);
                sort = Sort.by(Sort.Direction.ASC, "fecha", "horaSalida");
            }
        } else {
            sort = Sort.by(Sort.Direction.ASC, "fecha").and(Sort.by(Sort.Direction.ASC, "horaSalida")); // Orden por defecto
        }

        List<Viaje> viajesEncontrados = viajeRepository.findAll(spec, sort);
        if (viajesEncontrados.isEmpty()) {
            logger.info("No se encontraron viajes para el ómnibus ID {} con los criterios.", omnibusId);
            return new ArrayList<>();
        }
        logger.info("Se encontraron {} viajes para el ómnibus ID {} con los criterios.", viajesEncontrados.size(), omnibusId);
        return viajesEncontrados.stream()
                .map(this::mapToViajeResponseDTO)
                .collect(Collectors.toList());
    }

    public static class NoBusDisponibleException extends RuntimeException {
        public NoBusDisponibleException(String message) {
            super(message);
        }
    }
}