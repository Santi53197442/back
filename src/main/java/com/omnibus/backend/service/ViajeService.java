// src/main/java/com/omnibus/backend/service/ViajeService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.ViajeRequestDTO;
import com.omnibus.backend.dto.ViajeResponseDTO;
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

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class ViajeService {

    private static final Logger logger = LoggerFactory.getLogger(ViajeService.class);

    private static final Duration MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC = Duration.ofHours(12);
    private static final Duration MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES = Duration.ofHours(2);
    private static final Duration MIN_BUFFER_OPERATIVO_POST_LLEGADA = Duration.ofMinutes(30);

    @Autowired
    private ViajeRepository viajeRepository;
    @Autowired
    private LocalidadRepository localidadRepository;
    @Autowired
    private OmnibusRepository omnibusRepository;

    @Transactional
    public ViajeResponseDTO crearViaje(ViajeRequestDTO requestDTO) {
        logger.info("Iniciando proceso de creación de viaje: {}", requestDTO);
        // 0. Validaciones básicas
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

            // Lógica de selección de bus (tu código existente)
            // 1. VERIFICAR CONFLICTO HORARIO DIRECTO con el NUEVO VIAJE
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

            // 2. VERIFICAR UBICACIÓN y TIEMPO RESPECTO AL ÚLTIMO VIAJE ACTIVO DEL BUS
            Localidad ubicacionPrevistaDelBusParaNuevoViaje = busCandidato.getLocalidadActual();
            LocalDateTime horaLlegadaUltimoViajeDT = null;

            List<Viaje> ultimosViajesActivosList = viajeRepository.findUltimoViajeActivo(
                    busCandidato, requestDTO.getFecha(), requestDTO.getHoraSalida());

            if (!ultimosViajesActivosList.isEmpty()) {
                Viaje ultimoViajeActivoBus = ultimosViajesActivosList.get(0);
                ubicacionPrevistaDelBusParaNuevoViaje = ultimoViajeActivoBus.getDestino();
                horaLlegadaUltimoViajeDT = LocalDateTime.of(ultimoViajeActivoBus.getFecha(), ultimoViajeActivoBus.getHoraLlegada());
                logger.debug("Bus {} - Último viaje activo termina en {} a las {}", busCandidato.getMatricula(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre(), horaLlegadaUltimoViajeDT);
            } else {
                logger.debug("Bus {} no tiene viajes activos antes del nuevo viaje. Usando su localidadActual: {}", busCandidato.getMatricula(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre());
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

            // 3. VERIFICAR CON EL PRÓXIMO VIAJE YA ASIGNADO (PROGRAMADO) AL BUS
            List<Viaje> proximosViajesProgramadosList = viajeRepository.findProximoViajeProgramado(
                    busCandidato, requestDTO.getFecha(), requestDTO.getHoraLlegada());

            if (!proximosViajesProgramadosList.isEmpty()) {
                Viaje proximoViajeAsignado = proximosViajesProgramadosList.get(0);
                LocalDateTime salidaProximoViajeAsignadoDT = LocalDateTime.of(proximoViajeAsignado.getFecha(), proximoViajeAsignado.getHoraSalida());
                logger.debug("Bus {} - Próximo viaje asignado desde {} a las {}", busCandidato.getMatricula(), proximoViajeAsignado.getOrigen().getNombre(), salidaProximoViajeAsignadoDT);

                if (destinoNuevoViaje.getId().equals(proximoViajeAsignado.getOrigen().getId())) {
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES).isAfter(salidaProximoViajeAsignadoDT)) {
                        logger.debug("Bus {} - Conflicto buffer corto. Nuevo viaje llega {} (+{}h buffer) vs próximo asignado sale {}",
                                busCandidato.getMatricula(), llegadaNuevoViajeDT, MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES.toHours(), salidaProximoViajeAsignadoDT);
                        continue;
                    }
                } else {
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC).isAfter(salidaProximoViajeAsignadoDT)) {
                        logger.debug("Bus {} - Conflicto buffer general. Nuevo viaje llega {} (+{}h buffer) vs próximo asignado sale {}",
                                busCandidato.getMatricula(), llegadaNuevoViajeDT, MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC.toHours(), salidaProximoViajeAsignadoDT);
                        continue;
                    }
                }
            } else {
                logger.debug("Bus {} no tiene viajes programados después del nuevo viaje.", busCandidato.getMatricula());
            }

            logger.info("Bus {} SELECCIONADO para el nuevo viaje.", busCandidato.getMatricula());
            busSeleccionado = busCandidato;
            break;
        }


        if (busSeleccionado == null) {
            logger.warn("No se encontró ningún bus disponible que cumpla todos los criterios para el viaje solicitado.");
            throw new NoBusDisponibleException("No hay ómnibus disponibles que cumplan todos los criterios de horario y ubicación para el viaje solicitado.");
        }

        Viaje nuevoViaje = new Viaje();
        nuevoViaje.setFecha(requestDTO.getFecha());
        nuevoViaje.setHoraSalida(requestDTO.getHoraSalida());
        nuevoViaje.setHoraLlegada(requestDTO.getHoraLlegada());
        nuevoViaje.setOrigen(origenNuevoViaje);
        nuevoViaje.setDestino(destinoNuevoViaje);
        nuevoViaje.setBusAsignado(busSeleccionado);
        // --- RECOMENDACIÓN para crearViaje ---
        // Si añades `capacidadConfigurada` a Viaje:
        // nuevoViaje.setCapacidadConfigurada(busSeleccionado.getCapacidadAsientos());
        nuevoViaje.setAsientosDisponibles(busSeleccionado.getCapacidadAsientos()); // Capacidad total inicialmente disponible
        nuevoViaje.setEstado(EstadoViaje.PROGRAMADO);

        // --- RECOMENDACIÓN para crearViaje ---
        // Es buena práctica marcar el bus como asignado para evitar condiciones de carrera
        // y tener un estado más claro, si `EstadoBus.ASIGNADO_A_VIAJE` existe y se maneja.
        busSeleccionado.setEstado(EstadoBus.ASIGNADO_A_VIAJE); // CONSIDERA ESTO
        omnibusRepository.save(busSeleccionado);               // Y ESTO

        Viaje viajeGuardado = viajeRepository.save(nuevoViaje);
        logger.info("Viaje creado y guardado con ID: {}. Bus asignado: {}", viajeGuardado.getId(), busSeleccionado.getMatricula());

        return mapToViajeResponseDTO(viajeGuardado);
    }

    @Transactional
    public void finalizarViaje(Integer viajeId) {
        // Tu código existente de finalizarViaje...
        logger.info("Intentando finalizar viaje con ID: {}", viajeId);
        Viaje viaje = viajeRepository.findById(viajeId)
                .orElseThrow(() -> {
                    logger.error("Intento de finalizar viaje no encontrado: {}", viajeId);
                    return new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId);
                });

        if (viaje.getEstado() == EstadoViaje.FINALIZADO) {
            logger.warn("Viaje {} ya estaba finalizado. No se realizan cambios.", viajeId);
            return;
        }
        if (viaje.getEstado() == EstadoViaje.CANCELADO) {
            logger.warn("Viaje {} está cancelado. No se puede finalizar.", viajeId);
            throw new IllegalStateException("No se puede finalizar un viaje cancelado.");
        }

        Omnibus bus = viaje.getBusAsignado();
        if (bus == null) {
            logger.error("CRÍTICO: Viaje {} (estado {}) no tiene bus asignado al intentar finalizar.", viajeId, viaje.getEstado());
            throw new IllegalStateException("El viaje " + viajeId + " no tiene un bus asignado para finalizar.");
        }

        viaje.setEstado(EstadoViaje.FINALIZADO);
        viajeRepository.save(viaje);
        logger.info("Viaje {} marcado como FINALIZADO.", viajeId);

        Localidad destinoViaje = viaje.getDestino();
        bus.setLocalidadActual(destinoViaje);
        bus.setEstado(EstadoBus.OPERATIVO);
        omnibusRepository.save(bus);

        logger.info("Bus {} (ID: {}) actualizado. Nueva localidad: {} (ID: {}), Nuevo estado: {}",
                bus.getMatricula(), bus.getId(), destinoViaje.getNombre(), destinoViaje.getId(), bus.getEstado());
    }

    // --- NUEVO MÉTODO PARA REASIGNAR VIAJE ---
    @Transactional
    public ViajeResponseDTO reasignarViaje(Integer viajeId, Long nuevoOmnibusId)
            throws EntityNotFoundException, IllegalArgumentException, IllegalStateException, NoBusDisponibleException {

        logger.info("Iniciando reasignación del viaje ID {} al ómnibus ID {}", viajeId, nuevoOmnibusId);

        // 1. Recuperar entidades
        Viaje viaje = viajeRepository.findById(viajeId)
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId));

        Omnibus nuevoOmnibus = omnibusRepository.findById(nuevoOmnibusId)
                .orElseThrow(() -> new EntityNotFoundException("Nuevo ómnibus no encontrado con ID: " + nuevoOmnibusId));

        Omnibus omnibusAnterior = viaje.getBusAsignado(); // Puede ser null si el viaje nunca tuvo un bus asignado (error de datos)

        // 2. Validaciones iniciales
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO) { // Solo reasignar viajes PROGRAMADOS
            throw new IllegalStateException("El viaje con ID " + viajeId + " no está en estado PROGRAMADO. Estado actual: " + viaje.getEstado());
        }

        if (omnibusAnterior != null && omnibusAnterior.getId().equals(nuevoOmnibusId)) {
            throw new IllegalArgumentException("El nuevo ómnibus es el mismo que el actualmente asignado.");
        }

        if (nuevoOmnibus.getEstado() != EstadoBus.OPERATIVO) {
            throw new IllegalArgumentException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no está OPERATIVO. Estado actual: " + nuevoOmnibus.getEstado());
        }

        if (!nuevoOmnibus.getLocalidadActual().getId().equals(viaje.getOrigen().getId())) {
            throw new IllegalArgumentException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no se encuentra en la localidad de origen del viaje (" +
                    viaje.getOrigen().getNombre() + "). Se encuentra en: " + nuevoOmnibus.getLocalidadActual().getNombre());
        }

        // 3. Calcular pasajes vendidos y validar capacidad del nuevo ómnibus
        int pasajesVendidos;
        if (omnibusAnterior != null) {
            // IDEALMENTE: pasajesVendidos = viaje.getCapacidadConfigurada() - viaje.getAsientosDisponibles();
            // Si no tienes `capacidadConfigurada` en Viaje, usamos la capacidad del ómnibus anterior:
            pasajesVendidos = omnibusAnterior.getCapacidadAsientos() - viaje.getAsientosDisponibles();
        } else {
            // Si no había bus anterior, y el viaje está PROGRAMADO, esto es un estado inconsistente.
            // Asumimos 0 pasajes vendidos si no hay cómo calcularlo, pero es un warning.
            logger.warn("Viaje {} (PROGRAMADO) no tenía un ómnibus anterior asignado. Asumiendo 0 pasajes vendidos para reasignación.", viajeId);
            pasajesVendidos = 0; // O lanzar error si esto no debería pasar
        }
        if (pasajesVendidos < 0) pasajesVendidos = 0; // Por si asientosDisponibles > capacidadOmnibusAnterior (error de datos)


        if (nuevoOmnibus.getCapacidadAsientos() < pasajesVendidos) {
            throw new IllegalArgumentException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no tiene suficientes asientos. " +
                    "Pasajes vendidos: " + pasajesVendidos + ", Capacidad del nuevo ómnibus: " + nuevoOmnibus.getCapacidadAsientos());
        }

        // 4. Validar disponibilidad horaria del NUEVO ÓMNIBUS para el VIAJE (adaptado de crearViaje)
        LocalDateTime salidaViajeDT = LocalDateTime.of(viaje.getFecha(), viaje.getHoraSalida());
        LocalDateTime llegadaViajeDT = LocalDateTime.of(viaje.getFecha(), viaje.getHoraLlegada());

        logger.debug("Evaluando NUEVO ÓMNIBUS {} para reasignación del viaje ID {}", nuevoOmnibus.getMatricula(), viaje.getId());

        // 4.1. CONFLICTO HORARIO DIRECTO para el NUEVO OMNIBUS con otros viajes
        List<Viaje> viajesConflictivosDirectos = viajeRepository
                .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoInAndIdNot(
                        nuevoOmnibus,
                        viaje.getFecha(),
                        viaje.getHoraLlegada(), // Llegada del viaje a reasignar
                        viaje.getHoraSalida(),  // Salida del viaje a reasignar
                        List.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO),
                        viaje.getId() // Excluir el propio viaje de esta comprobación
                );
        if (!viajesConflictivosDirectos.isEmpty()) {
            Viaje conflicto = viajesConflictivosDirectos.get(0);
            logger.warn("Nuevo ómnibus {} tiene conflicto horario directo con viaje ID {} para reasignación.", nuevoOmnibus.getMatricula(), conflicto.getId());
            throw new NoBusDisponibleException("El nuevo ómnibus seleccionado (ID: " + nuevoOmnibusId + ") tiene un conflicto horario directo con otro de sus viajes (ID: " + conflicto.getId() + ").");
        }

        // 4.2. UBICACIÓN y TIEMPO DEL NUEVO OMNIBUS (desde su último viaje) RESPECTO AL VIAJE A REASIGNAR
        // Esta lógica es la misma que en crearViaje, aplicada al nuevoOmnibus y al viaje actual.
        Localidad ubicacionPrevistaNuevoOmnibus = nuevoOmnibus.getLocalidadActual();
        LocalDateTime horaLlegadaUltimoViajeNuevoOmnibusDT = null;

        List<Viaje> ultimosViajesActivosNuevoOmnibusList = viajeRepository.findUltimoViajeActivo(
                nuevoOmnibus, viaje.getFecha(), viaje.getHoraSalida());

        if (!ultimosViajesActivosNuevoOmnibusList.isEmpty()) {
            Viaje ultimoViajeActivoNuevoOmnibus = ultimosViajesActivosNuevoOmnibusList.get(0);
            // Asegurarse de no considerar el mismo viaje si por alguna razón está en esta lista (no debería si el estado es PROGRAMADO)
            if (!ultimoViajeActivoNuevoOmnibus.getId().equals(viaje.getId())) {
                ubicacionPrevistaNuevoOmnibus = ultimoViajeActivoNuevoOmnibus.getDestino();
                horaLlegadaUltimoViajeNuevoOmnibusDT = LocalDateTime.of(ultimoViajeActivoNuevoOmnibus.getFecha(), ultimoViajeActivoNuevoOmnibus.getHoraLlegada());
                logger.debug("Nuevo ómnibus {} - Último viaje activo termina en {} a las {}", nuevoOmnibus.getMatricula(), ubicacionPrevistaNuevoOmnibus.getNombre(), horaLlegadaUltimoViajeNuevoOmnibusDT);
            }
        } else {
            logger.debug("Nuevo ómnibus {} no tiene viajes activos antes del viaje a reasignar. Usando su localidadActual: {}", nuevoOmnibus.getMatricula(), ubicacionPrevistaNuevoOmnibus.getNombre());
        }

        // Ya validamos que nuevoOmnibus.getLocalidadActual() == viaje.getOrigen() si no tiene viajes previos.
        // Aquí verificamos si, viniendo de un viaje anterior, llega al origen del viaje a reasignar.
        if (!ubicacionPrevistaNuevoOmnibus.getId().equals(viaje.getOrigen().getId())) {
            logger.warn("Nuevo ómnibus {} no estará en la localidad de origen {} para el viaje. Estará en {}", nuevoOmnibus.getMatricula(), viaje.getOrigen().getNombre(), ubicacionPrevistaNuevoOmnibus.getNombre());
            throw new NoBusDisponibleException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no estará en la localidad de origen del viaje (" + viaje.getOrigen().getNombre() + ") a tiempo.");
        }

        if (horaLlegadaUltimoViajeNuevoOmnibusDT != null) {
            if (horaLlegadaUltimoViajeNuevoOmnibusDT.plus(MIN_BUFFER_OPERATIVO_POST_LLEGADA).isAfter(salidaViajeDT)) {
                logger.warn("Nuevo ómnibus {} no tiene suficiente tiempo de preparación en origen. Llega a las {} (+{} min) vs salida del viaje a reasignar {}",
                        nuevoOmnibus.getMatricula(), horaLlegadaUltimoViajeNuevoOmnibusDT, MIN_BUFFER_OPERATIVO_POST_LLEGADA.toMinutes(), salidaViajeDT);
                throw new NoBusDisponibleException("El nuevo ómnibus (ID: " + nuevoOmnibusId + ") no tiene suficiente tiempo de preparación en origen.");
            }
        }

        // 4.3. CONFLICTO CON PRÓXIMO VIAJE YA ASIGNADO AL NUEVO OMNIBUS
        List<Viaje> proximosViajesProgramadosNuevoOmnibusList = viajeRepository.findProximoViajeProgramado(
                nuevoOmnibus, viaje.getFecha(), viaje.getHoraLlegada());

        if (!proximosViajesProgramadosNuevoOmnibusList.isEmpty()) {
            Viaje proximoViajeAsignadoNuevoOmnibus = proximosViajesProgramadosNuevoOmnibusList.get(0);
            // Asegurarse de no considerar el mismo viaje si por alguna razón está en esta lista (no debería si el estado es PROGRAMADO)
            if (!proximoViajeAsignadoNuevoOmnibus.getId().equals(viaje.getId())) {
                LocalDateTime salidaProximoViajeAsignadoNuevoOmnibusDT = LocalDateTime.of(proximoViajeAsignadoNuevoOmnibus.getFecha(), proximoViajeAsignadoNuevoOmnibus.getHoraSalida());
                logger.debug("Nuevo ómnibus {} - Próximo viaje asignado (ID {}) desde {} a las {}", nuevoOmnibus.getMatricula(), proximoViajeAsignadoNuevoOmnibus.getId(), proximoViajeAsignadoNuevoOmnibus.getOrigen().getNombre(), salidaProximoViajeAsignadoNuevoOmnibusDT);

                if (viaje.getDestino().getId().equals(proximoViajeAsignadoNuevoOmnibus.getOrigen().getId())) { // Misma localidad
                    if (llegadaViajeDT.plus(MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES).isAfter(salidaProximoViajeAsignadoNuevoOmnibusDT)) {
                        logger.warn("Nuevo ómnibus {} - Conflicto buffer corto. Viaje a reasignar llega {} (+{}h) vs próximo asignado (ID {}) sale {}",
                                nuevoOmnibus.getMatricula(), llegadaViajeDT, MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES.toHours(), proximoViajeAsignadoNuevoOmnibus.getId(), salidaProximoViajeAsignadoNuevoOmnibusDT);
                        throw new NoBusDisponibleException("Conflicto de buffer (misma localidad) con el próximo viaje del nuevo ómnibus (ID: " + proximoViajeAsignadoNuevoOmnibus.getId() + ").");
                    }
                } else { // Diferente localidad
                    if (llegadaViajeDT.plus(MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC).isAfter(salidaProximoViajeAsignadoNuevoOmnibusDT)) {
                        logger.warn("Nuevo ómnibus {} - Conflicto buffer general. Viaje a reasignar llega {} (+{}h) vs próximo asignado (ID {}) sale {}",
                                nuevoOmnibus.getMatricula(), llegadaViajeDT, MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC.toHours(), proximoViajeAsignadoNuevoOmnibus.getId(), salidaProximoViajeAsignadoNuevoOmnibusDT);
                        throw new NoBusDisponibleException("Conflicto de buffer (diferente localidad) con el próximo viaje del nuevo ómnibus (ID: " + proximoViajeAsignadoNuevoOmnibus.getId() + ").");
                    }
                }
            }
        } else {
            logger.debug("Nuevo ómnibus {} no tiene viajes programados después del viaje a reasignar.", nuevoOmnibus.getMatricula());
        }

        // 5. Realizar la reasignación
        logger.info("Todas las validaciones para reasignación del viaje ID {} a ómnibus ID {} pasaron. Procediendo con la actualización.", viajeId, nuevoOmnibusId);

        //  - Liberar ómnibus anterior (si existía)
        if (omnibusAnterior != null) {
            omnibusAnterior.setEstado(EstadoBus.OPERATIVO);
            // La localidad del ómnibus anterior NO cambia aquí, ya que el viaje no se completó/inició con él.
            // Sigue en la localidad donde estaba (probablemente origen del viaje que se le quita).
            omnibusRepository.save(omnibusAnterior);
            logger.info("Ómnibus anterior (ID {}) puesto en estado OPERATIVO.", omnibusAnterior.getId());
        }

        //  - Actualizar viaje
        viaje.setBusAsignado(nuevoOmnibus);
        // "Se seteará la cantidad de asientos del nuevo ómnibus como la nueva cantidad máxima de pasajes vendibles para dicho viaje"
        // Esto significa que la capacidad "total" del viaje ahora es la del nuevo bus.
        // Los asientos disponibles serán esta nueva capacidad MENOS los ya vendidos.
        // IDEALMENTE: viaje.setCapacidadConfigurada(nuevoOmnibus.getCapacidadAsientos());
        viaje.setAsientosDisponibles(nuevoOmnibus.getCapacidadAsientos() - pasajesVendidos);

        //  - Actualizar nuevo ómnibus
        nuevoOmnibus.setEstado(EstadoBus.ASIGNADO_A_VIAJE); // Marcar el nuevo bus como asignado
        // La localidad del nuevo ómnibus no cambia, ya está en el origen.
        omnibusRepository.save(nuevoOmnibus);
        logger.info("Nuevo ómnibus (ID {}) asignado al viaje ID {} y puesto en estado ASIGNADO_A_VIAJE.", nuevoOmnibus.getId(), viajeId);

        Viaje viajeActualizado = viajeRepository.save(viaje);
        logger.info("Viaje ID {} reasignado y guardado exitosamente.", viajeActualizado.getId());

        // 6. Mapear y retornar
        return mapToViajeResponseDTO(viajeActualizado);
    }


    private ViajeResponseDTO mapToViajeResponseDTO(Viaje viaje) {
        return ViajeResponseDTO.builder()
                .id(viaje.getId())
                .fecha(viaje.getFecha())
                .horaSalida(viaje.getHoraSalida())
                .horaLlegada(viaje.getHoraLlegada())
                .origenId(viaje.getOrigen().getId())
                .origenNombre(viaje.getOrigen().getNombre())
                .destinoId(viaje.getDestino().getId())
                .destinoNombre(viaje.getDestino().getNombre())
                .busAsignadoId(viaje.getBusAsignado() != null ? viaje.getBusAsignado().getId() : null)
                .busMatricula(viaje.getBusAsignado() != null ? viaje.getBusAsignado().getMatricula() : "N/A")
                .asientosDisponibles(viaje.getAsientosDisponibles())
                // Si quieres mostrar el estado del viaje en la respuesta:
                .estado(viaje.getEstado() != null ? viaje.getEstado().name() : null)
                // Si quieres mostrar la capacidad total del viaje (con el bus actual):
                // .capacidadTotal(viaje.getBusAsignado() != null ? viaje.getBusAsignado().getCapacidadAsientos() : 0)
                // O si tienes `capacidadConfigurada`:
                // .capacidadTotal(viaje.getCapacidadConfigurada())
                .build();
    }

    public static class NoBusDisponibleException extends RuntimeException {
        public NoBusDisponibleException(String message) {
            super(message);
        }
    }
}