// src/main/java/com/omnibus/backend/service/ViajeService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.ViajeRequestDTO;
import com.omnibus.backend.dto.ViajeResponseDTO;
import com.omnibus.backend.model.*; // Importa todos tus modelos
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ViajeService {

    private static final Logger logger = LoggerFactory.getLogger(ViajeService.class);

    // Constantes para buffers (podrían estar en un archivo de propiedades o como constantes de clase)
    // AJUSTA ESTOS VALORES SEGÚN TUS NECESIDADES
    private static final Duration MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC = Duration.ofHours(12);
    private static final Duration MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES = Duration.ofHours(2);
    private static final Duration MIN_BUFFER_OPERATIVO_POST_LLEGADA = Duration.ofMinutes(30); // Tiempo mínimo que un bus debe estar en destino antes de otra salida

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

        // Buscar buses que estén OPERATIVOS (no EN_MANTENIMIENTO, etc.)
        // La localidad actual se verificará más adelante
        List<Omnibus> busesPotenciales = omnibusRepository.findByEstado(EstadoBus.OPERATIVO);
        if (busesPotenciales.isEmpty()){
            throw new NoBusDisponibleException("No hay ómnibus en estado OPERATIVO en el sistema.");
        }

        Omnibus busSeleccionado = null;

        for (Omnibus busCandidato : busesPotenciales) {
            logger.debug("Evaluando bus candidato: {}", busCandidato.getMatricula());

            // 1. VERIFICAR CONFLICTO HORARIO DIRECTO con el NUEVO VIAJE
            // Solo considera viajes ya PROGRAMADOS o EN_CURSO para este bus
            List<Viaje> viajesConflictivosDirectos = viajeRepository
                    .findByBusAsignadoAndFechaAndHoraSalidaBeforeAndHoraLlegadaAfterAndEstadoIn(
                            busCandidato,
                            requestDTO.getFecha(),
                            requestDTO.getHoraLlegada(), // Llegada del nuevo
                            requestDTO.getHoraSalida(),  // Salida del nuevo
                            List.of(EstadoViaje.PROGRAMADO, EstadoViaje.EN_CURSO) // Estados relevantes
                    );
            if (!viajesConflictivosDirectos.isEmpty()) {
                logger.debug("Bus {} tiene conflicto directo para el nuevo viaje.", busCandidato.getMatricula());
                continue; // Bus ocupado directamente, probar el siguiente
            }

            // 2. VERIFICAR UBICACIÓN y TIEMPO RESPECTO AL ÚLTIMO VIAJE ACTIVO DEL BUS
            Localidad ubicacionPrevistaDelBusParaNuevoViaje = busCandidato.getLocalidadActual(); // Ubicación si no tiene viajes activos
            LocalDateTime horaLlegadaUltimoViajeDT = null; // Hora a la que llega de su último viaje activo

            List<Viaje> ultimosViajesActivosList = viajeRepository.findUltimoViajeActivo(
                    busCandidato, requestDTO.getFecha(), requestDTO.getHoraSalida());

            if (!ultimosViajesActivosList.isEmpty()) {
                Viaje ultimoViajeActivoBus = ultimosViajesActivosList.get(0); // El más reciente por el ORDER BY
                ubicacionPrevistaDelBusParaNuevoViaje = ultimoViajeActivoBus.getDestino();
                horaLlegadaUltimoViajeDT = LocalDateTime.of(ultimoViajeActivoBus.getFecha(), ultimoViajeActivoBus.getHoraLlegada());
                logger.debug("Bus {} - Último viaje activo termina en {} a las {}", busCandidato.getMatricula(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre(), horaLlegadaUltimoViajeDT);
            } else {
                logger.debug("Bus {} no tiene viajes activos antes del nuevo viaje. Usando su localidadActual: {}", busCandidato.getMatricula(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre());
            }

            // ¿Estará el bus en el origen del nuevo viaje?
            if (!ubicacionPrevistaDelBusParaNuevoViaje.getId().equals(origenNuevoViaje.getId())) {
                logger.debug("Bus {} no estará en la localidad de origen {} para el nuevo viaje. Estará en {}", busCandidato.getMatricula(), origenNuevoViaje.getNombre(), ubicacionPrevistaDelBusParaNuevoViaje.getNombre());
                continue; // Bus no estará en el lugar correcto
            }

            // ¿Hay suficiente tiempo desde que el bus LLEGA al origen (de su último viaje) hasta la SALIDA del nuevo viaje?
            if (horaLlegadaUltimoViajeDT != null) { // Si tuvo un último viaje activo
                if (horaLlegadaUltimoViajeDT.plus(MIN_BUFFER_OPERATIVO_POST_LLEGADA).isAfter(salidaNuevoViajeDT)) {
                    logger.debug("Bus {} no tiene suficiente tiempo de preparación en origen. Llega a las {} (+{} min buffer) vs salida nuevo viaje {}",
                            busCandidato.getMatricula(), horaLlegadaUltimoViajeDT, MIN_BUFFER_OPERATIVO_POST_LLEGADA.toMinutes(), salidaNuevoViajeDT);
                    continue; // No hay tiempo suficiente para prepararse para el nuevo viaje
                }
            }
            // Si horaLlegadaUltimoViajeDT es null, significa que el bus está en su localidadActual y no tiene viajes previos cercanos.

            // 3. VERIFICAR CON EL PRÓXIMO VIAJE YA ASIGNADO (PROGRAMADO) AL BUS
            List<Viaje> proximosViajesProgramadosList = viajeRepository.findProximoViajeProgramado(
                    busCandidato, requestDTO.getFecha(), requestDTO.getHoraLlegada()); // Referencia es la llegada del nuevo viaje

            if (!proximosViajesProgramadosList.isEmpty()) {
                Viaje proximoViajeAsignado = proximosViajesProgramadosList.get(0); // El más cercano por el ORDER BY
                LocalDateTime salidaProximoViajeAsignadoDT = LocalDateTime.of(proximoViajeAsignado.getFecha(), proximoViajeAsignado.getHoraSalida());
                logger.debug("Bus {} - Próximo viaje asignado desde {} a las {}", busCandidato.getMatricula(), proximoViajeAsignado.getOrigen().getNombre(), salidaProximoViajeAsignadoDT);

                // Si el nuevo viaje termina en la misma localidad donde empieza el próximo viaje asignado
                if (destinoNuevoViaje.getId().equals(proximoViajeAsignado.getOrigen().getId())) {
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES).isAfter(salidaProximoViajeAsignadoDT)) {
                        logger.debug("Bus {} - Conflicto buffer corto. Nuevo viaje llega {} (+{}h buffer) vs próximo asignado sale {}",
                                busCandidato.getMatricula(), llegadaNuevoViajeDT, MIN_BUFFER_MISMA_LOCALIDAD_ENTRE_VIAJES.toHours(), salidaProximoViajeAsignadoDT);
                        continue; // No hay suficiente buffer (ej. 2h) aunque sea misma localidad
                    }
                } else { // El nuevo viaje termina en una localidad DIFERENTE a donde empieza el próximo
                    if (llegadaNuevoViajeDT.plus(MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC).isAfter(salidaProximoViajeAsignadoDT)) {
                        logger.debug("Bus {} - Conflicto buffer general. Nuevo viaje llega {} (+{}h buffer) vs próximo asignado sale {}",
                                busCandidato.getMatricula(), llegadaNuevoViajeDT, MIN_BUFFER_GENERAL_ENTRE_VIAJES_DIF_LOC.toHours(), salidaProximoViajeAsignadoDT);
                        continue;
                    }
                }
            } else {
                logger.debug("Bus {} no tiene viajes programados después del nuevo viaje.", busCandidato.getMatricula());
            }

            // Si pasó todas las validaciones, este bus es el candidato
            logger.info("Bus {} SELECCIONADO para el nuevo viaje.", busCandidato.getMatricula());
            busSeleccionado = busCandidato;
            break; // Encontramos un bus, salimos del bucle
        }


        if (busSeleccionado == null) {
            logger.warn("No se encontró ningún bus disponible que cumpla todos los criterios para el viaje solicitado.");
            throw new NoBusDisponibleException("No hay ómnibus disponibles que cumplan todos los criterios de horario y ubicación para el viaje solicitado.");
        }

        // 3. Crear y guardar el Viaje
        Viaje nuevoViaje = new Viaje();
        nuevoViaje.setFecha(requestDTO.getFecha());
        nuevoViaje.setHoraSalida(requestDTO.getHoraSalida());
        nuevoViaje.setHoraLlegada(requestDTO.getHoraLlegada());
        nuevoViaje.setOrigen(origenNuevoViaje);
        nuevoViaje.setDestino(destinoNuevoViaje);
        nuevoViaje.setBusAsignado(busSeleccionado);
        nuevoViaje.setAsientosDisponibles(busSeleccionado.getCapacidadAsientos());
        nuevoViaje.setEstado(EstadoViaje.PROGRAMADO); // Estado inicial

        Viaje viajeGuardado = viajeRepository.save(nuevoViaje);
        logger.info("Viaje creado y guardado con ID: {}. Bus asignado: {}", viajeGuardado.getId(), busSeleccionado.getMatricula());

        // !!! IMPORTANTE: NO actualizar localidad del bus aquí !!!
        // Se podría actualizar el estado del bus si es necesario, por ejemplo:
        // busSeleccionado.setEstado(EstadoBus.ASIGNADO_A_VIAJE); // O mantenerlo OPERATIVO si puede tomar más viajes
        // omnibusRepository.save(busSeleccionado); // Guardar si cambias estado del bus

        // 5. Mapear a DTO de respuesta
        return mapToViajeResponseDTO(viajeGuardado);
    }

    // NUEVO MÉTODO: finalizarViaje
    @Transactional
    public void finalizarViaje(Integer viajeId) {
        logger.info("Intentando finalizar viaje con ID: {}", viajeId);
        Viaje viaje = viajeRepository.findById(viajeId)
                .orElseThrow(() -> {
                    logger.error("Intento de finalizar viaje no encontrado: {}", viajeId);
                    return new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId);
                });

        if (viaje.getEstado() == EstadoViaje.FINALIZADO) {
            logger.warn("Viaje {} ya estaba finalizado. No se realizan cambios.", viajeId);
            return; // O lanzar excepción si se considera un error
        }
        if (viaje.getEstado() == EstadoViaje.CANCELADO) {
            logger.warn("Viaje {} está cancelado. No se puede finalizar.", viajeId);
            throw new IllegalStateException("No se puede finalizar un viaje cancelado.");
        }


        Omnibus bus = viaje.getBusAsignado();
        if (bus == null) {
            // Esto no debería ocurrir si un viaje PROGRAMADO o EN_CURSO siempre tiene bus
            logger.error("CRÍTICO: Viaje {} (estado {}) no tiene bus asignado al intentar finalizar.", viajeId, viaje.getEstado());
            throw new IllegalStateException("El viaje " + viajeId + " no tiene un bus asignado para finalizar.");
        }

        // Actualizar estado del viaje
        viaje.setEstado(EstadoViaje.FINALIZADO);
        viajeRepository.save(viaje);
        logger.info("Viaje {} marcado como FINALIZADO.", viajeId);

        // Actualizar ómnibus
        Localidad destinoViaje = viaje.getDestino();
        bus.setLocalidadActual(destinoViaje);
        bus.setEstado(EstadoBus.OPERATIVO); // Bus vuelve a estar operativo en su nueva localidad
        omnibusRepository.save(bus);

        logger.info("Bus {} (ID: {}) actualizado. Nueva localidad: {} (ID: {}), Nuevo estado: {}",
                bus.getMatricula(), bus.getId(), destinoViaje.getNombre(), destinoViaje.getId(), bus.getEstado());
    }


    // mapToViajeResponseDTO (existente, pero asegúrate que mapee el nuevo EstadoViaje si lo incluyes en el DTO)
    private ViajeResponseDTO mapToViajeResponseDTO(Viaje viaje) {
        // Considera añadir viaje.getEstado().toString() al DTO si es útil para el frontend
        return ViajeResponseDTO.builder()
                .id(viaje.getId())
                .fecha(viaje.getFecha())
                .horaSalida(viaje.getHoraSalida())
                .horaLlegada(viaje.getHoraLlegada())
                .origenId(viaje.getOrigen().getId())
                .origenNombre(viaje.getOrigen().getNombre())
                .destinoId(viaje.getDestino().getId())
                .destinoNombre(viaje.getDestino().getNombre())
                .busAsignadoId(viaje.getBusAsignado().getId())
                .busMatricula(viaje.getBusAsignado().getMatricula())
                .asientosDisponibles(viaje.getAsientosDisponibles())
                // .estado(viaje.getEstado().name()) // Opcional: si quieres devolver el estado del viaje
                .build();
    }

    // Custom Exception para buses no disponibles (existente)
    public static class NoBusDisponibleException extends RuntimeException {
        public NoBusDisponibleException(String message) {
            super(message);
        }
    }
}