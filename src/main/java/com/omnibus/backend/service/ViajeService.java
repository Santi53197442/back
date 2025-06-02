// src/main/java/com/omnibus/backend/service/ViajeService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.*;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.LocalidadRepository;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;
import com.omnibus.backend.repository.PasajeRepository;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
    private final PasajeRepository pasajeRepository;

    @Autowired
    public ViajeService(ViajeRepository viajeRepository,
                        LocalidadRepository localidadRepository,
                        OmnibusRepository omnibusRepository,
                        PasajeRepository pasajeRepository) {
        this.viajeRepository = viajeRepository;
        this.localidadRepository = localidadRepository;
        this.omnibusRepository = omnibusRepository;
        this.pasajeRepository = pasajeRepository;
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
        if (requestDTO.getPrecio() == null || requestDTO.getPrecio() <= 0) {
            throw new IllegalArgumentException("El precio del viaje debe ser un valor positivo.");
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
            logger.debug("Evaluando bus candidato para NUEVO VIAJE: {} (ID: {})", busCandidato.getMatricula(), busCandidato.getId());

            List<Viaje> viajesConflictivosDirectos = viajeRepository
                    .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoIn(
                            busCandidato,
                            requestDTO.getFecha(),
                            requestDTO.getHoraLlegada(),
                            requestDTO.getHoraSalida(),
                            Arrays.asList(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO)
                    );
            if (!viajesConflictivosDirectos.isEmpty()) {
                logger.debug("Bus {} tiene conflicto horario directo para el nuevo viaje con viaje ID {}.", busCandidato.getMatricula(), viajesConflictivosDirectos.get(0).getId());
                continue;
            }

            Localidad ubicacionPrevistaDelBusParaNuevoViaje = busCandidato.getLocalidadActual();
            LocalDateTime horaLlegadaUltimoViajeDT = null;

            List<Viaje> ultimosViajesActivosList = viajeRepository.findUltimoViajeActivoConcluidoAntesDe( // Nombre corregido
                    busCandidato,
                    requestDTO.getFecha(),
                    requestDTO.getHoraSalida(),
                    Arrays.asList(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO)
            );

            if (!ultimosViajesActivosList.isEmpty()) {
                Viaje ultimoViajeActivoBus = ultimosViajesActivosList.get(0);
                ubicacionPrevistaDelBusParaNuevoViaje = ultimoViajeActivoBus.getDestino();
                horaLlegadaUltimoViajeDT = LocalDateTime.of(ultimoViajeActivoBus.getFecha(), ultimoViajeActivoBus.getHoraLlegada());
                logger.debug("Último viaje del bus {}: ID {}, llega a {} a las {}", busCandidato.getMatricula(), ultimoViajeActivoBus.getId(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre(), horaLlegadaUltimoViajeDT);
            } else {
                logger.debug("Bus {} no tiene viajes previos activos. Ubicación actual: {}", busCandidato.getMatricula(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre());
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

            List<Viaje> proximosViajesProgramadosList = viajeRepository.findProximoViajeProgramadoComenzandoDespuesDe( // Nombre corregido
                    busCandidato,
                    requestDTO.getFecha(),
                    requestDTO.getHoraLlegada(),
                    Arrays.asList(EstadoViaje.PROGRAMADO)
            );

            if (!proximosViajesProgramadosList.isEmpty()) {
                Viaje proximoViajeAsignado = proximosViajesProgramadosList.get(0);
                LocalDateTime salidaProximoViajeAsignadoDT = LocalDateTime.of(proximoViajeAsignado.getFecha(), proximoViajeAsignado.getHoraSalida());
                logger.debug("Próximo viaje del bus {}: ID {}, sale de {} a las {}", busCandidato.getMatricula(), proximoViajeAsignado.getId(), proximoViajeAsignado.getOrigen().getNombre(), salidaProximoViajeAsignadoDT);

                Duration bufferNecesario;
                if (destinoNuevoViaje.getId().equals(proximoViajeAsignado.getOrigen().getId())) {
                    bufferNecesario = MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES;
                } else {
                    bufferNecesario = MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC;
                }

                if (llegadaNuevoViajeDT.plus(bufferNecesario).isAfter(salidaProximoViajeAsignadoDT)) {
                    logger.debug("Bus {} no tiene suficiente buffer ({} min) antes del próximo viaje ID {}. Llegada nuevo: {}, Salida próximo: {}",
                            busCandidato.getMatricula(), bufferNecesario.toMinutes(), proximoViajeAsignado.getId(), llegadaNuevoViajeDT, salidaProximoViajeAsignadoDT);
                    continue;
                }
            }
            busSeleccionado = busCandidato;
            logger.info("Bus {} (ID: {}) SELECCIONADO para el nuevo viaje.", busCandidato.getMatricula(), busCandidato.getId());
            break;
        }

        if (busSeleccionado == null) {
            throw new NoBusDisponibleException("No hay ómnibus disponibles que cumplan todos los criterios de horario, ubicación y buffers de tiempo.");
        }

        Viaje nuevoViaje = Viaje.builder()
                .fecha(requestDTO.getFecha())
                .horaSalida(requestDTO.getHoraSalida())
                .horaLlegada(requestDTO.getHoraLlegada())
                .origen(origenNuevoViaje)
                .destino(destinoNuevoViaje)
                .busAsignado(busSeleccionado)
                .asientosDisponibles(busSeleccionado.getCapacidadAsientos())
                .estado(EstadoViaje.PROGRAMADO)
                .precio(requestDTO.getPrecio())
                .build();

        busSeleccionado.setEstado(EstadoBus.ASIGNADO_A_VIAJE);
        omnibusRepository.save(busSeleccionado);

        Viaje viajeGuardado = viajeRepository.save(nuevoViaje);
        logger.info("Viaje creado ID: {}. Precio: {}. Bus asignado: {} (ID: {})",
                viajeGuardado.getId(), viajeGuardado.getPrecio(), busSeleccionado.getMatricula(), busSeleccionado.getId());
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
            throw new IllegalStateException("No se puede finalizar un viaje que está CANCELADO.");
        }
        if (viaje.getEstado() != EstadoViaje.EN_CURSO) {
            throw new IllegalStateException("Solo se pueden finalizar viajes EN_CURSO. Estado actual: " + viaje.getEstado());
        }

        Omnibus bus = viaje.getBusAsignado();
        if (bus == null) {
            throw new IllegalStateException("El viaje " + viajeId + " (EN_CURSO) no tiene bus asignado, no se puede finalizar correctamente.");
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
            throw new IllegalStateException("El viaje con ID " + viajeId + " no está PROGRAMADO. Estado: " + viaje.getEstado() + ". No se puede reasignar.");
        }
        if (omnibusAnterior != null && omnibusAnterior.getId().equals(nuevoOmnibusId)) {
            throw new IllegalArgumentException("El nuevo ómnibus es el mismo que el actualmente asignado.");
        }
        if (nuevoOmnibus.getEstado() != EstadoBus.OPERATIVO) {
            throw new IllegalArgumentException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no está OPERATIVO. Estado: " + nuevoOmnibus.getEstado());
        }
        if (!nuevoOmnibus.getLocalidadActual().getId().equals(viaje.getOrigen().getId())) {
            throw new IllegalArgumentException("El nuevo ómnibus no se encuentra en la localidad de origen del viaje (" +
                    viaje.getOrigen().getNombre() + "). Está en " + nuevoOmnibus.getLocalidadActual().getNombre());
        }

        List<EstadoPasaje> estadosPasajeOcupado = Arrays.asList(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);
        long pasajesVendidosCount = pasajeRepository.countByDatosViajeAndEstadoIn(viaje, estadosPasajeOcupado);
        int pasajesVendidos = (int) pasajesVendidosCount;

        if (nuevoOmnibus.getCapacidadAsientos() < pasajesVendidos) {
            throw new IllegalArgumentException("El nuevo ómnibus no tiene suficientes asientos ("+ nuevoOmnibus.getCapacidadAsientos() +
                    ") para los pasajes ya vendidos (" + pasajesVendidos + ").");
        }

        List<Viaje> viajesConflictivosNuevoBus = viajeRepository
                .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoInAndIdNot(
                        nuevoOmnibus,
                        viaje.getFecha(),
                        viaje.getHoraLlegada(),
                        viaje.getHoraSalida(),
                        Arrays.asList(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO),
                        viaje.getId()
                );
        if (!viajesConflictivosNuevoBus.isEmpty()) {
            throw new NoBusDisponibleException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") tiene conflicto horario directo con otro viaje (ID: " + viajesConflictivosNuevoBus.get(0).getId() + ").");
        }

        // TODO: Añadir lógica de validación de buffers para el nuevoOmnibus
        // similar a la de crearViaje, usando findUltimoViajeActivoConcluidoAntesDe
        // y findProximoViajeProgramadoComenzandoDespuesDe para el `nuevoOmnibus`
        // y las horas/fechas del `viaje` que se está reasignando.


        if (omnibusAnterior != null) {
            omnibusAnterior.setEstado(EstadoBus.OPERATIVO);
            omnibusRepository.save(omnibusAnterior);
        }

        viaje.setBusAsignado(nuevoOmnibus);
        viaje.setAsientosDisponibles(nuevoOmnibus.getCapacidadAsientos() - pasajesVendidos);
        nuevoOmnibus.setEstado(EstadoBus.ASIGNADO_A_VIAJE);
        omnibusRepository.save(nuevoOmnibus);

        Viaje viajeActualizado = viajeRepository.save(viaje);
        logger.info("Viaje ID {} reasignado a ómnibus ID {}. Asientos disponibles ahora: {}", viajeId, nuevoOmnibusId, viajeActualizado.getAsientosDisponibles());
        return mapToViajeResponseDTO(viajeActualizado);
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
            if (dto.getEstadoViaje() == null) {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("estado"), EstadoViaje.PROGRAMADO),
                        criteriaBuilder.equal(root.get("estado"), EstadoViaje.EN_CURSO)
                ));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = Sort.unsorted();
        String defaultSortField1 = "fecha";
        String defaultSortField2 = "horaSalida";

        if (dto.getOrdenarPor() != null && !dto.getOrdenarPor().trim().isEmpty()) {
            String campoOrden = dto.getOrdenarPor().trim();
            if (Arrays.asList("fecha", "horaSalida", "horaLlegada", "estado", "id", "precio").contains(campoOrden)) {
                Sort.Direction direction = (dto.getDireccionOrden() != null && "DESC".equalsIgnoreCase(dto.getDireccionOrden().trim()))
                        ? Sort.Direction.DESC : Sort.Direction.ASC;
                sort = Sort.by(direction, campoOrden);
            } else {
                logger.warn("Campo de ordenamiento no válido: {}. Usando orden por defecto (fecha ASC, horaSalida ASC).", campoOrden);
                sort = Sort.by(Sort.Direction.ASC, defaultSortField1).and(Sort.by(Sort.Direction.ASC, defaultSortField2));
            }
        } else {
            sort = Sort.by(Sort.Direction.ASC, defaultSortField1).and(Sort.by(Sort.Direction.ASC, defaultSortField2));
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

    @Transactional(readOnly = true)
    public List<ViajeConDisponibilidadDTO> buscarViajesConDisponibilidad(BusquedaViajesGeneralDTO criterios) {
        logger.debug("Buscando viajes con disponibilidad. Criterios: {}", criterios);

        Specification<Viaje> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (criterios.getOrigenId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("origen").get("id"), criterios.getOrigenId()));
            }
            if (criterios.getDestinoId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("destino").get("id"), criterios.getDestinoId()));
            }
            if (criterios.getFechaDesde() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fecha"), criterios.getFechaDesde()));
            }
            if (criterios.getFechaHasta() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("fecha"), criterios.getFechaHasta()));
            }
            if (criterios.getEstado() != null) {
                predicates.add(criteriaBuilder.equal(root.get("estado"), criterios.getEstado()));
            } else {
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("estado"), EstadoViaje.PROGRAMADO),
                        criteriaBuilder.equal(root.get("estado"), EstadoViaje.EN_CURSO)
                ));
            }
            predicates.add(criteriaBuilder.isNotNull(root.get("busAsignado")));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        List<Viaje> viajesFiltrados = viajeRepository.findAll(spec);
        logger.debug("Viajes filtrados por BD: {}", viajesFiltrados.size());

        List<ViajeConDisponibilidadDTO> dtos = viajesFiltrados.stream().map(viaje -> {
                    Omnibus omnibus = viaje.getBusAsignado();
                    if (omnibus == null) {
                        logger.warn("Viaje ID {} sin ómnibus asignado (inesperado después del filtro), se omitirá.", viaje.getId());
                        return null;
                    }
                    int capacidadTotal = omnibus.getCapacidadAsientos();
                    List<EstadoPasaje> estadosOcupados = Arrays.asList(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);
                    long asientosVendidosCount = pasajeRepository.countByDatosViajeAndEstadoIn(viaje, estadosOcupados);
                    LocalDateTime fechaSalidaCompleta = LocalDateTime.of(viaje.getFecha(), viaje.getHoraSalida());
                    LocalDateTime fechaLlegadaCompleta = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());

                    return new ViajeConDisponibilidadDTO(
                            viaje.getId(),
                            fechaSalidaCompleta,
                            fechaLlegadaCompleta,
                            viaje.getOrigen().getNombre(),
                            viaje.getDestino().getNombre(),
                            omnibus.getMatricula(),
                            capacidadTotal,
                            (int) asientosVendidosCount,
                            viaje.getEstado(),
                            viaje.getPrecio()
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (criterios.getMinAsientosDisponibles() != null) {
            dtos = dtos.stream()
                    .filter(dto -> dto.getAsientosDisponibles() >= criterios.getMinAsientosDisponibles())
                    .collect(Collectors.toList());
        }

        if (criterios.getSortBy() != null && !criterios.getSortBy().isEmpty()) {
            Comparator<ViajeConDisponibilidadDTO> comparator = null;
            boolean ascending = criterios.getSortDir() == null || "asc".equalsIgnoreCase(criterios.getSortDir());

            switch (criterios.getSortBy().toLowerCase()) {
                case "fechasalida":
                    comparator = Comparator.comparing(ViajeConDisponibilidadDTO::getFechaSalida, Comparator.nullsLast(LocalDateTime::compareTo));
                    break;
                case "origennombre":
                    comparator = Comparator.comparing(ViajeConDisponibilidadDTO::getOrigenNombre, String.CASE_INSENSITIVE_ORDER);
                    break;
                case "destinonombre":
                    comparator = Comparator.comparing(ViajeConDisponibilidadDTO::getDestinoNombre, String.CASE_INSENSITIVE_ORDER);
                    break;
                case "asientosdisponibles":
                    comparator = Comparator.comparingInt(ViajeConDisponibilidadDTO::getAsientosDisponibles);
                    break;
                case "precio":
                    comparator = Comparator.comparing(ViajeConDisponibilidadDTO::getPrecio, Comparator.nullsLast(Double::compareTo));
                    break;
                default:
                    logger.warn("Criterio de ordenamiento no reconocido para ViajeConDisponibilidadDTO: '{}'.", criterios.getSortBy());
            }

            if (comparator != null) {
                if (!ascending) {
                    comparator = comparator.reversed();
                }
                dtos.sort(comparator);
            }
        } else {
            dtos.sort(Comparator.comparing(ViajeConDisponibilidadDTO::getFechaSalida, Comparator.nullsLast(LocalDateTime::compareTo)));
        }
        return dtos;
    }

    @Transactional(readOnly = true)
    public ViajeDetalleConAsientosDTO obtenerDetallesViajeParaSeleccionAsientos(Integer viajeId) {
        logger.debug("Obteniendo detalles y asientos para el viaje ID: {}", viajeId);

        Viaje viaje = viajeRepository.findById(viajeId)
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId));

        if (viaje.getBusAsignado() == null) {
            throw new IllegalStateException("El viaje con ID " + viajeId + " no tiene un ómnibus asignado.");
        }
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO && viaje.getEstado() != EstadoViaje.EN_CURSO) {
            throw new IllegalStateException("No se pueden seleccionar asientos para un viaje que no está PROGRAMADO o EN CURSO. Estado actual: " + viaje.getEstado());
        }

        Omnibus omnibus = viaje.getBusAsignado();
        List<EstadoPasaje> estadosOcupados = Arrays.asList(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);
        Set<Integer> numerosAsientoOcupados = pasajeRepository.findByDatosViajeAndEstadoIn(viaje, estadosOcupados)
                .stream()
                .map(Pasaje::getNumeroAsiento)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        logger.debug("Asientos ocupados para viaje {}: {}", viajeId, numerosAsientoOcupados);

        return ViajeDetalleConAsientosDTO.builder()
                .id(viaje.getId())
                .fecha(viaje.getFecha())
                .horaSalida(viaje.getHoraSalida())
                .horaLlegada(viaje.getHoraLlegada())
                .origenNombre(viaje.getOrigen().getNombre())
                .destinoNombre(viaje.getDestino().getNombre())
                .precio(viaje.getPrecio())
                .estado(viaje.getEstado())
                .omnibusMatricula(omnibus.getMatricula())
                .capacidadOmnibus(omnibus.getCapacidadAsientos())
                .numerosAsientoOcupados(numerosAsientoOcupados)
                .build();
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
        } else {
            builder.busMatricula("N/A");
        }

        if (viaje.getEstado() != null) {
            builder.estado(viaje.getEstado().name());
        }
        if (viaje.getPrecio() != null) {
            builder.precio(viaje.getPrecio());
        }
        return builder.build();
    }

    public static class NoBusDisponibleException extends RuntimeException {
        public NoBusDisponibleException(String message) {
            super(message);
        }
    }
}