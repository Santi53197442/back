// src/main/java/com/omnibus/backend/service/ViajeService.java
package com.omnibus.backend.service;

// Tus imports existentes
import com.omnibus.backend.dto.BusquedaViajesOmnibusDTO;
import com.omnibus.backend.dto.ViajeRequestDTO;
import com.omnibus.backend.dto.ViajeResponseDTO;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.LocalidadRepository;
import com.omnibus.backend.repository.OmnibusRepository;
import com.omnibus.backend.repository.ViajeRepository;

// NUEVOS IMPORTS
import com.omnibus.backend.dto.BusquedaViajesGeneralDTO;   // Para el nuevo método
import com.omnibus.backend.dto.ViajeConDisponibilidadDTO; // Para el nuevo método
import com.omnibus.backend.repository.PasajeRepository;   // Para manejar Pasajes

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
// import java.time.LocalTime; // No parece usarse directamente LocalTime como tipo de campo en Viaje
import java.util.ArrayList;
import java.util.Arrays; // Para List.of o Arrays.asList
import java.util.Comparator; // Para el nuevo método
import java.util.List;
import java.util.Objects; // Para el nuevo método (filter Objects::nonNull)
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
    private final PasajeRepository pasajeRepository; // NUEVO: Repositorio de Pasajes

    @Autowired
    public ViajeService(ViajeRepository viajeRepository,
                        LocalidadRepository localidadRepository,
                        OmnibusRepository omnibusRepository,
                        PasajeRepository pasajeRepository) { // NUEVO: Inyectar PasajeRepository
        this.viajeRepository = viajeRepository;
        this.localidadRepository = localidadRepository;
        this.omnibusRepository = omnibusRepository;
        this.pasajeRepository = pasajeRepository; // NUEVO
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
        // ... (tu lógica compleja para seleccionar busSeleccionado se mantiene igual)
        for (Omnibus busCandidato : busesPotenciales) {
            logger.debug("Evaluando bus candidato para NUEVO VIAJE: {}", busCandidato.getMatricula());
            List<Viaje> viajesConflictivosDirectos = viajeRepository
                    .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoIn( // Asegúrate que estos parámetros son correctos para tu query
                            busCandidato,
                            requestDTO.getFecha(),
                            requestDTO.getHoraLlegada(), // hora llegada del nuevo viaje
                            requestDTO.getHoraSalida(),  // hora salida del nuevo viaje
                            Arrays.asList(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO)
                    );
            if (!viajesConflictivosDirectos.isEmpty()) {
                logger.debug("Bus {} tiene conflicto directo para el nuevo viaje.", busCandidato.getMatricula());
                continue;
            }
            Localidad ubicacionPrevistaDelBusParaNuevoViaje = busCandidato.getLocalidadActual();
            LocalDateTime horaLlegadaUltimoViajeDT = null;

            // Asumo que findUltimoViajeActivo te da el último viaje ANTES de la salida del nuevo viaje
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

            // Asumo que findProximoViajeProgramado te da el próximo viaje DESPUÉS de la llegada del nuevo viaje
            List<Viaje> proximosViajesProgramadosList = viajeRepository.findProximoViajeProgramado(
                    busCandidato, requestDTO.getFecha(), requestDTO.getHoraLlegada());
            if (!proximosViajesProgramadosList.isEmpty()) {
                Viaje proximoViajeAsignado = proximosViajesProgramadosList.get(0);
                LocalDateTime salidaProximoViajeAsignadoDT = LocalDateTime.of(proximoViajeAsignado.getFecha(), proximoViajeAsignado.getHoraSalida());
                if (destinoNuevoViaje.getId().equals(proximoViajeAsignado.getOrigen().getId())) { // Si el bus se queda en la misma localidad
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES).isAfter(salidaProximoViajeAsignadoDT)) {
                        logger.debug("Bus {} no tiene suficiente buffer (misma loc) para próximo viaje {}", busCandidato.getMatricula(), proximoViajeAsignado.getId());
                        continue;
                    }
                } else { // Si el bus tiene que moverse a otra localidad para el próximo viaje
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC).isAfter(salidaProximoViajeAsignadoDT)) {
                        logger.debug("Bus {} no tiene suficiente buffer (dif loc) para próximo viaje {}", busCandidato.getMatricula(), proximoViajeAsignado.getId());
                        continue;
                    }
                }
            }
            busSeleccionado = busCandidato;
            logger.info("Bus {} SELECCIONADO para el nuevo viaje.", busCandidato.getMatricula());
            break;
        }

        if (busSeleccionado == null) {
            throw new NoBusDisponibleException("No hay ómnibus disponibles que cumplan todos los criterios de horario y ubicación.");
        }

        Viaje nuevoViaje = new Viaje();
        nuevoViaje.setFecha(requestDTO.getFecha());
        nuevoViaje.setHoraSalida(requestDTO.getHoraSalida());
        nuevoViaje.setHoraLlegada(requestDTO.getHoraLlegada());
        nuevoViaje.setOrigen(origenNuevoViaje);
        nuevoViaje.setDestino(destinoNuevoViaje);
        nuevoViaje.setBusAsignado(busSeleccionado);
        nuevoViaje.setAsientosDisponibles(busSeleccionado.getCapacidadAsientos()); // Inicialmente todos disponibles
        nuevoViaje.setEstado(EstadoViaje.PROGRAMADO);
        // nuevoViaje.setPrecio(requestDTO.getPrecio()); // Si ViajeRequestDTO tiene precio y Viaje también

        busSeleccionado.setEstado(EstadoBus.ASIGNADO_A_VIAJE); // O el estado que corresponda
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
            return; // O lanzar excepción si se prefiere
        }
        if (viaje.getEstado() == EstadoViaje.CANCELADO) {
            throw new IllegalStateException("No se puede finalizar un viaje que está CANCELADO.");
        }
        if (viaje.getEstado() != EstadoViaje.EN_CURSO) { // Solo se debería poder finalizar un viaje EN_CURSO
            throw new IllegalStateException("Solo se pueden finalizar viajes EN_CURSO. Estado actual: " + viaje.getEstado());
        }

        Omnibus bus = viaje.getBusAsignado();
        if (bus == null) {
            // Esto no debería pasar si un viaje EN_CURSO siempre tiene bus
            throw new IllegalStateException("El viaje " + viajeId + " (EN_CURSO) no tiene bus asignado, no se puede finalizar correctamente.");
        }

        viaje.setEstado(EstadoViaje.FINALIZADO);
        viajeRepository.save(viaje);

        Localidad destinoViaje = viaje.getDestino();
        bus.setLocalidadActual(destinoViaje);
        bus.setEstado(EstadoBus.OPERATIVO); // El bus vuelve a estar operativo
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
            throw new IllegalArgumentException("El nuevo ómnibus es el mismo que el actualmente asignado. No se requiere reasignación.");
        }
        if (nuevoOmnibus.getEstado() != EstadoBus.OPERATIVO) {
            throw new IllegalArgumentException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no está OPERATIVO. Estado: " + nuevoOmnibus.getEstado());
        }

        // Verificar ubicación del nuevo ómnibus
        if (!nuevoOmnibus.getLocalidadActual().getId().equals(viaje.getOrigen().getId())) {
            throw new IllegalArgumentException("El nuevo ómnibus no se encuentra en la localidad de origen del viaje (" +
                    viaje.getOrigen().getNombre() + "). Está en " + nuevoOmnibus.getLocalidadActual().getNombre());
        }

        // Calcular pasajes ya vendidos para el viaje
        // Esto asume que Viaje.asientosDisponibles se actualiza correctamente cuando se venden pasajes.
        // O, de forma más robusta, contar los pasajes directamente:
        List<EstadoPasaje> estadosPasajeOcupado = Arrays.asList(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);
        long pasajesVendidosCount = pasajeRepository.countByDatosViajeAndEstadoIn(viaje, estadosPasajeOcupado);
        int pasajesVendidos = (int) pasajesVendidosCount;

        if (nuevoOmnibus.getCapacidadAsientos() < pasajesVendidos) {
            throw new IllegalArgumentException("El nuevo ómnibus no tiene suficientes asientos ("+ nuevoOmnibus.getCapacidadAsientos() +
                    ") para los pasajes ya vendidos (" + pasajesVendidos + ").");
        }

        // ... (tu lógica compleja de validación de horarios para el nuevoOmnibus se mantiene igual)
        // Solo asegúrate de excluir el viaje actual (viajeId) de las comprobaciones de conflicto para nuevoOmnibus
        LocalDateTime salidaViajeDT = LocalDateTime.of(viaje.getFecha(), viaje.getHoraSalida());
        LocalDateTime llegadaViajeDT = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());

        List<Viaje> viajesConflictivosDirectos = viajeRepository
                .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoInAndIdNot(
                        nuevoOmnibus, viaje.getFecha(), viaje.getHoraLlegada(), viaje.getHoraSalida(),
                        Arrays.asList(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO), viaje.getId() // Excluye el viaje actual
                );
        if (!viajesConflictivosDirectos.isEmpty()) {
            throw new NoBusDisponibleException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") tiene conflicto horario directo con otro viaje (ID: " + viajesConflictivosDirectos.get(0).getId() + ").");
        }
        // ... resto de validaciones de buffer para el nuevoOmnibus ... (similar a crearViaje pero excluyendo el 'viaje' actual de los chequeos si es necesario)

        // Liberar el ómnibus anterior si existía
        if (omnibusAnterior != null) {
            omnibusAnterior.setEstado(EstadoBus.OPERATIVO);
            omnibusRepository.save(omnibusAnterior);
        }

        // Asignar nuevo ómnibus al viaje
        viaje.setBusAsignado(nuevoOmnibus);
        viaje.setAsientosDisponibles(nuevoOmnibus.getCapacidadAsientos() - pasajesVendidos); // Actualizar asientos disponibles
        nuevoOmnibus.setEstado(EstadoBus.ASIGNADO_A_VIAJE); // O el estado que corresponda
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
                // Suponiendo que Viaje.fecha es LocalDate
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fecha"), dto.getFechaDesde()));
            }
            if (dto.getFechaHasta() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("fecha"), dto.getFechaHasta()));
            }
            if (dto.getEstadoViaje() != null) {
                predicates.add(criteriaBuilder.equal(root.get("estado"), dto.getEstadoViaje()));
            }
            // Considerar agregar un filtro por defecto para no mostrar CANCELADO o FINALIZADO si no se especifica
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
            // Validar que el campo de orden sea uno de los atributos de Viaje o Viaje_
            if (Arrays.asList("fecha", "horaSalida", "horaLlegada", "estado", "id" /*, otros campos válidos de Viaje */).contains(campoOrden)) {
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

    // --- NUEVO MÉTODO ---
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

            // Asumiendo que Viaje.fecha es LocalDate y BusquedaViajesGeneralDTO.fechaDesde/Hasta son LocalDate
            if (criterios.getFechaDesde() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("fecha"), criterios.getFechaDesde()));
            }
            if (criterios.getFechaHasta() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("fecha"), criterios.getFechaHasta()));
            }


            if (criterios.getEstado() != null) {
                predicates.add(criteriaBuilder.equal(root.get("estado"), criterios.getEstado()));
            } else {
                // Por defecto, solo mostrar viajes PROGRAMADOS o EN_CURSO si no se especifica estado
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.equal(root.get("estado"), EstadoViaje.PROGRAMADO),
                        criteriaBuilder.equal(root.get("estado"), EstadoViaje.EN_CURSO)
                ));
            }
            // Asegurar que el viaje tenga un bus asignado para poder calcular capacidad
            predicates.add(criteriaBuilder.isNotNull(root.get("busAsignado")));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        List<Viaje> viajesFiltrados = viajeRepository.findAll(spec);
        logger.debug("Viajes filtrados por BD: {}", viajesFiltrados.size());

        List<ViajeConDisponibilidadDTO> dtos = viajesFiltrados.stream().map(viaje -> {
                    Omnibus omnibus = viaje.getBusAsignado();
                    // El predicado isNotNull(root.get("busAsignado")) ya debería asegurar esto, pero una doble verificación no daña.
                    if (omnibus == null) {
                        logger.warn("El viaje con ID {} fue filtrado pero no tiene ómnibus asignado. Se omitirá.", viaje.getId());
                        return null;
                    }
                    int capacidadTotal = omnibus.getCapacidadAsientos();

                    List<EstadoPasaje> estadosOcupados = Arrays.asList(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);
                    long asientosVendidosCount = pasajeRepository.countByDatosViajeAndEstadoIn(viaje, estadosOcupados);

                    LocalDateTime fechaSalidaCompleta = LocalDateTime.of(viaje.getFecha(), viaje.getHoraSalida());
                    LocalDateTime fechaLlegadaCompleta = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());


                    return new ViajeConDisponibilidadDTO(
                            viaje.getId(),
                            fechaSalidaCompleta, // Usar LocalDateTime completo
                            fechaLlegadaCompleta, // Usar LocalDateTime completo
                            viaje.getOrigen().getNombre(),
                            viaje.getDestino().getNombre(),
                            omnibus.getMatricula(),
                            capacidadTotal,
                            (int) asientosVendidosCount,
                            viaje.getEstado(),
                            viaje.getPrecio() // Asegúrate que Viaje tiene un campo precio y que se establece
                    );
                })
                .filter(Objects::nonNull) // Remover nulos si algún viaje se coló sin ómnibus
                .collect(Collectors.toList());

        logger.debug("Viajes mapeados a DTO antes de filtrar por asientos disponibles: {}", dtos.size());

        if (criterios.getMinAsientosDisponibles() != null) {
            dtos = dtos.stream()
                    .filter(dto -> dto.getAsientosDisponibles() >= criterios.getMinAsientosDisponibles())
                    .collect(Collectors.toList());
            logger.debug("Viajes después de filtrar por minAsientosDisponibles ({}): {}", criterios.getMinAsientosDisponibles(), dtos.size());
        }

        if (criterios.getSortBy() != null && !criterios.getSortBy().isEmpty()) {
            Comparator<ViajeConDisponibilidadDTO> comparator = null;
            boolean ascending = criterios.getSortDir() == null || "asc".equalsIgnoreCase(criterios.getSortDir());

            switch (criterios.getSortBy().toLowerCase()) {
                case "fechasalida": // Este es LocalDateTime en ViajeConDisponibilidadDTO
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
                    comparator = Comparator.comparing(ViajeConDisponibilidadDTO::getPrecio, Comparator.nullsLast(Double::compareTo)); // Asume que precio es Double
                    break;
                default:
                    logger.warn("Criterio de ordenamiento no reconocido: '{}'. Se usará orden por defecto.", criterios.getSortBy());
            }

            if (comparator != null) {
                if (!ascending) {
                    comparator = comparator.reversed();
                }
                dtos.sort(comparator);
                logger.debug("Viajes ordenados por {} {}", criterios.getSortBy(), ascending ? "ASC" : "DESC");
            }
        } else {
            // Ordenamiento por defecto si no se especifica nada (ej: por fecha de salida ascendente)
            dtos.sort(Comparator.comparing(ViajeConDisponibilidadDTO::getFechaSalida, Comparator.nullsLast(LocalDateTime::compareTo)));
            logger.debug("Viajes ordenados por fechaSalida ASC (defecto)");
        }
        return dtos;
    }


    // Tu método helper mapToViajeResponseDTO
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
                .asientosDisponibles(viaje.getAsientosDisponibles()); // Este es el del Viaje entity

        if (viaje.getBusAsignado() != null) {
            builder.busAsignadoId(viaje.getBusAsignado().getId());
            builder.busMatricula(viaje.getBusAsignado().getMatricula());
            // Considera agregar la capacidad total del bus si ViajeResponseDTO lo necesita
            // builder.capacidadTotal(viaje.getBusAsignado().getCapacidadAsientos());
        } else {
            builder.busMatricula("N/A"); // O null, según prefieras
        }

        if (viaje.getEstado() != null) {
            builder.estado(viaje.getEstado().name());
        }
        // if (viaje.getPrecio() != null) { // Si Viaje tiene precio
        //     builder.precio(viaje.getPrecio());
        // }
        return builder.build();
    }

    // Tu clase de excepción personalizada
    public static class NoBusDisponibleException extends RuntimeException {
        public NoBusDisponibleException(String message) {
            super(message);
        }
    }
}