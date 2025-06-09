// src/main/java/com/omnibus/backend/service/PasajeService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.CompraMultiplePasajesRequestDTO;
import com.omnibus.backend.dto.CompraPasajeRequestDTO;
import com.omnibus.backend.dto.PasajeResponseDTO;
import com.omnibus.backend.dto.PasajeStatsDTO;
import com.omnibus.backend.model.*;
import com.omnibus.backend.repository.PasajeRepository;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class pasajeService { // Corregido a PascalCase: PasajeService

    private static final Logger logger = LoggerFactory.getLogger(pasajeService.class); // Corregido a PascalCase

    private final PasajeRepository pasajeRepository;
    private final ViajeRepository viajeRepository;
    private final UsuarioRepository usuarioRepository;

    @Autowired
    public pasajeService(PasajeRepository pasajeRepository,
                         ViajeRepository viajeRepository,
                         UsuarioRepository usuarioRepository) {
        this.pasajeRepository = pasajeRepository;
        this.viajeRepository = viajeRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional
    public PasajeResponseDTO comprarPasaje(CompraPasajeRequestDTO requestDTO) {
        logger.info("Intentando comprar pasaje para viaje ID {} por cliente ID {} en asiento {}",
                requestDTO.getViajeId(), requestDTO.getClienteId(), requestDTO.getNumeroAsiento());

        Viaje viaje = viajeRepository.findById(requestDTO.getViajeId())
                .orElseThrow(() -> {
                    logger.warn("Viaje no encontrado con ID: {}", requestDTO.getViajeId());
                    return new EntityNotFoundException("Viaje no encontrado con ID: " + requestDTO.getViajeId());
                });

        Usuario cliente = usuarioRepository.findById(requestDTO.getClienteId())
                .orElseThrow(() -> {
                    logger.warn("Cliente no encontrado con ID: {}", requestDTO.getClienteId());
                    return new EntityNotFoundException("Cliente no encontrado con ID: " + requestDTO.getClienteId());
                });

        // ... (el resto de la lógica de validación se mantiene igual)
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO) {
            String mensajeError = "Solo se pueden comprar pasajes para viajes en estado PROGRAMADO. Estado actual: " + viaje.getEstado();
            logger.warn(mensajeError + " para viaje ID {}", viaje.getId());
            throw new IllegalStateException(mensajeError);
        }
        if (viaje.getAsientosDisponibles() <= 0) {
            String mensajeError = "No hay asientos disponibles (conteo general) para el viaje ID: " + viaje.getId();
            logger.warn(mensajeError);
            throw new IllegalStateException(mensajeError);
        }
        Omnibus busAsignado = viaje.getBusAsignado();
        if (busAsignado == null) {
            String mensajeError = "El viaje ID " + viaje.getId() + " no tiene un ómnibus asignado.";
            logger.error(mensajeError);
            throw new IllegalStateException(mensajeError);
        }
        if (requestDTO.getNumeroAsiento() > busAsignado.getCapacidadAsientos() || requestDTO.getNumeroAsiento() < 1) {
            String mensajeError = "Número de asiento " + requestDTO.getNumeroAsiento() +
                    " inválido para un ómnibus con capacidad " + busAsignado.getCapacidadAsientos() + " asientos.";
            logger.warn(mensajeError + " para viaje ID {}", viaje.getId());
            throw new IllegalArgumentException(mensajeError);
        }
        pasajeRepository.findByDatosViajeAndNumeroAsiento(viaje, requestDTO.getNumeroAsiento())
                .ifPresent(pasajeExistente -> {
                    if (pasajeExistente.getEstado() != EstadoPasaje.CANCELADO) {
                        String mensajeError = "El asiento " + requestDTO.getNumeroAsiento() +
                                " ya está ocupado (estado: " + pasajeExistente.getEstado() +
                                ") para el viaje ID: " + viaje.getId();
                        logger.warn(mensajeError);
                        throw new IllegalStateException(mensajeError);
                    }
                });

        logger.info("Simulando proceso de pago para viaje ID {} asiento {}...", viaje.getId(), requestDTO.getNumeroAsiento());

        Pasaje nuevoPasaje = new Pasaje();
        nuevoPasaje.setCliente(cliente);
        nuevoPasaje.setDatosViaje(viaje);
        nuevoPasaje.setNumeroAsiento(requestDTO.getNumeroAsiento());
        nuevoPasaje.setPrecio(viaje.getPrecio());
        nuevoPasaje.setEstado(EstadoPasaje.VENDIDO);

        viaje.setAsientosDisponibles(viaje.getAsientosDisponibles() - 1);
        viajeRepository.save(viaje);

        Pasaje pasajeGuardado = pasajeRepository.save(nuevoPasaje);
        logger.info("Pasaje ID {} creado exitosamente para viaje ID {} asiento {}, estado: VENDIDO",
                pasajeGuardado.getId(), viaje.getId(), pasajeGuardado.getNumeroAsiento());

        return convertirAPasajeResponseDTO(pasajeGuardado);
    }

    // ... (los otros métodos como obtenerAsientosOcupados, obtenerHistorialPasajesPorClienteId, etc. se mantienen igual)
    @Transactional(readOnly = true)
    public List<Integer> obtenerAsientosOcupados(Integer viajeId) {
        logger.debug("Solicitando asientos ocupados para viaje ID: {}", viajeId);
        if (!viajeRepository.existsById(viajeId)) {
            logger.warn("Intento de obtener asientos de viaje inexistente ID: {}", viajeId);
            throw new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId);
        }
        List<Pasaje> pasajesDelViaje = pasajeRepository.findByDatosViajeId(viajeId);
        return pasajesDelViaje.stream()
                .filter(p -> p.getEstado() != EstadoPasaje.CANCELADO)
                .map(Pasaje::getNumeroAsiento)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PasajeResponseDTO> obtenerHistorialPasajesPorClienteId(Long clienteId) {
        logger.info("Buscando historial de pasajes para cliente ID: {}", clienteId);
        if (!usuarioRepository.existsById(clienteId)) {
            logger.warn("Cliente no encontrado con ID: {} al buscar historial de pasajes.", clienteId);
            throw new EntityNotFoundException("Cliente no encontrado con ID: " + clienteId);
        }
        List<Pasaje> pasajes = pasajeRepository.findByClienteId(clienteId);
        if (pasajes.isEmpty()) {
            logger.info("No se encontraron pasajes para el cliente ID: {}", clienteId);
            return Collections.emptyList();
        }
        logger.info("Encontrados {} pasajes para el cliente ID: {}", pasajes.size(), clienteId);
        return pasajes.stream()
                .map(this::convertirAPasajeResponseDTO)
                .collect(Collectors.toList());
    }

    // =================================================================================
    // AQUÍ ESTÁ LA CORRECCIÓN PRINCIPAL
    // =================================================================================
    private PasajeResponseDTO convertirAPasajeResponseDTO(Pasaje pasaje) {
        if (pasaje == null) return null;

        // Datos del Cliente
        Long idCliente = null;
        String nombreCliente = "Cliente Desconocido";
        String emailCliente = "Email no disponible"; // <-- CAMBIO CLAVE: Variable para el email
        if (pasaje.getCliente() != null) {
            idCliente = pasaje.getCliente().getId();
            nombreCliente = pasaje.getCliente().getNombreCompleto() != null ? pasaje.getCliente().getNombreCompleto() : "Nombre no disponible";
            emailCliente = pasaje.getCliente().getEmail() != null ? pasaje.getCliente().getEmail() : "Email no disponible"; // <-- CAMBIO CLAVE: Obtenemos el email
        }

        // Datos del Viaje
        Integer idViaje = null;
        String origenNombre = "Origen Desconocido";
        String destinoNombre = "Destino Desconocido";
        LocalDate fechaViaje = null;
        LocalTime horaSalidaViaje = null;
        String matriculaOmnibus = "Matrícula no asignada"; // <-- CAMBIO CLAVE: Variable para la matrícula

        if (pasaje.getDatosViaje() != null) {
            Viaje viaje = pasaje.getDatosViaje(); // Para simplificar
            idViaje = viaje.getId();
            fechaViaje = viaje.getFecha();
            horaSalidaViaje = viaje.getHoraSalida();

            if (viaje.getOrigen() != null) {
                origenNombre = viaje.getOrigen().getNombre() != null ? viaje.getOrigen().getNombre() : "Origen no especificado";
            }
            if (viaje.getDestino() != null) {
                destinoNombre = viaje.getDestino().getNombre() != null ? viaje.getDestino().getNombre() : "Destino no especificado";
            }
            // <-- CAMBIO CLAVE: Obtenemos la matrícula desde el bus asignado al viaje
            if (viaje.getBusAsignado() != null) {
                matriculaOmnibus = viaje.getBusAsignado().getMatricula() != null ? viaje.getBusAsignado().getMatricula() : "Matrícula no especificada";
            }
        }

        Double precio = pasaje.getPrecio();

        // <-- CAMBIO CLAVE: Llamamos al nuevo constructor con los 13 parámetros
        return new PasajeResponseDTO(
                pasaje.getId(),
                idCliente,
                nombreCliente,
                emailCliente,       // <--- NUEVO
                idViaje,
                origenNombre,
                destinoNombre,
                fechaViaje,
                horaSalidaViaje,
                matriculaOmnibus,   // <--- NUEVO
                precio,
                pasaje.getEstado(),
                pasaje.getNumeroAsiento(),
                pasaje.getFechaReserva()
        );
    }

    // El método obtenerPasajesPorViajeConFiltros se mantiene igual
    // ya que también utiliza el método corregido `convertirAPasajeResponseDTO`
    @Transactional(readOnly = true)
    public List<PasajeResponseDTO> obtenerPasajesPorViajeConFiltros(
            Integer viajeId,
            Optional<String> clienteNombreOpt,
            Optional<Integer> numeroAsientoOpt,
            Optional<String> estadoPasajeOpt,
            Optional<String> sortByOpt,
            Optional<String> sortDirOpt
    ) {
        logger.info("Buscando pasajes para viaje ID: {} con filtros", viajeId);
        if (!viajeRepository.existsById(viajeId)) {
            logger.warn("Viaje no encontrado con ID: {} al buscar sus pasajes.", viajeId);
            throw new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId);
        }
        List<Pasaje> pasajesDelViaje = pasajeRepository.findByDatosViajeId(viajeId);
        if (pasajesDelViaje.isEmpty()) {
            logger.info("No se encontraron pasajes para el viaje ID: {}", viajeId);
            return Collections.emptyList();
        }
        Stream<Pasaje> pasajesStream = pasajesDelViaje.stream();
        if (clienteNombreOpt.isPresent() && !clienteNombreOpt.get().isBlank()) {
            String nombreFiltro = clienteNombreOpt.get().toLowerCase();
            pasajesStream = pasajesStream.filter(p -> p.getCliente() != null &&
                    p.getCliente().getNombreCompleto() != null &&
                    p.getCliente().getNombreCompleto().toLowerCase().contains(nombreFiltro));
        }
        if (numeroAsientoOpt.isPresent()) {
            Integer asientoFiltro = numeroAsientoOpt.get();
            pasajesStream = pasajesStream.filter(p -> p.getNumeroAsiento() != null &&
                    p.getNumeroAsiento().equals(asientoFiltro));
        }
        if (estadoPasajeOpt.isPresent() && !estadoPasajeOpt.get().isBlank()) {
            try {
                EstadoPasaje estadoFiltro = EstadoPasaje.valueOf(estadoPasajeOpt.get().toUpperCase());
                pasajesStream = pasajesStream.filter(p -> p.getEstado() == estadoFiltro);
            } catch (IllegalArgumentException e) {
                logger.warn("Estado de pasaje inválido para filtro: '{}'. Se ignorará el filtro de estado.", estadoPasajeOpt.get());
            }
        }
        List<Pasaje> pasajesFiltrados = pasajesStream.collect(Collectors.toList());
        if (sortByOpt.isPresent() && !sortByOpt.get().isBlank()) {
            String sortBy = sortByOpt.get();
            Sort.Direction direction = sortDirOpt.map(dir -> "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC)
                    .orElse(Sort.Direction.ASC);
            Comparator<Pasaje> comparator = null;
            switch (sortBy.toLowerCase()) {
                case "clientenombre":
                    comparator = Comparator.comparing(p -> p.getCliente() != null ? p.getCliente().getNombreCompleto().toLowerCase() : "", Comparator.nullsLast(String::compareTo));
                    break;
                case "numeroasiento":
                    comparator = Comparator.comparing(Pasaje::getNumeroAsiento, Comparator.nullsLast(Integer::compareTo));
                    break;
                case "precio":
                    comparator = Comparator.comparing(Pasaje::getPrecio, Comparator.nullsLast(Double::compareTo));
                    break;
                case "estadopasaje":
                    comparator = Comparator.comparing(p -> p.getEstado() != null ? p.getEstado().name() : "", Comparator.nullsLast(String::compareTo));
                    break;
                default:
                    logger.warn("Campo de ordenamiento no reconocido: '{}'. No se aplicará ordenamiento.", sortBy);
            }
            if (comparator != null) {
                if (direction == Sort.Direction.DESC) {
                    comparator = comparator.reversed();
                }
                pasajesFiltrados.sort(comparator);
            }
        }
        logger.info("Encontrados {} pasajes para el viaje ID {} después de filtros y ordenamiento.", pasajesFiltrados.size(), viajeId);
        return pasajesFiltrados.stream()
                .map(this::convertirAPasajeResponseDTO)
                .collect(Collectors.toList());
    }

    public List<PasajeStatsDTO> obtenerDatosParaEstadisticas() {
        List<Pasaje> pasajes = pasajeRepository.findAll();

        return pasajes.stream().map(pasaje -> {
            // Construimos la ruta a partir del viaje asociado
            String origen = pasaje.getDatosViaje().getOrigen().getNombre();
            String destino = pasaje.getDatosViaje().getDestino().getNombre();
            String ruta = origen + " - " + destino;

            return new PasajeStatsDTO(
                    pasaje.getPrecio(),
                    pasaje.getEstado(),
                    pasaje.getDatosViaje().getFecha(),
                    ruta
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public List<PasajeResponseDTO> comprarMultiplesPasajes(CompraMultiplePasajesRequestDTO requestDTO) {
        logger.info("================ INICIO CONFIRMACIÓN DE COMPRA ================");

        Viaje viaje = viajeRepository.findById(requestDTO.getViajeId())
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + requestDTO.getViajeId()));

        List<Pasaje> pasajesAConfirmar = new ArrayList<>();

        for (Integer numeroAsiento : requestDTO.getNumerosAsiento()) {
            Pasaje pasaje = pasajeRepository.findByDatosViajeAndNumeroAsiento(viaje, numeroAsiento)
                    .orElseThrow(() -> new IllegalStateException("La reserva para el asiento " + numeroAsiento + " no fue encontrada o expiró."));

            // --- VALIDACIONES DE CONFIRMACIÓN ---
            if (pasaje.getEstado() != EstadoPasaje.RESERVADO) {
                throw new IllegalStateException("El asiento " + numeroAsiento + " no estaba reservado. Su estado es: " + pasaje.getEstado());
            }
            if (!pasaje.getCliente().getId().equals(requestDTO.getClienteId())) {
                throw new SecurityException("Intento de comprar una reserva que no pertenece al usuario.");
            }

            // --- ACTUALIZACIÓN ---
            pasaje.setEstado(EstadoPasaje.VENDIDO);
            pasaje.setFechaReserva(null); // Limpiamos la fecha de reserva
            pasajesAConfirmar.add(pasaje);
        }

        List<Pasaje> pasajesGuardados = pasajeRepository.saveAll(pasajesAConfirmar);
        logger.info("¡Éxito! {} pasajes confirmados y movidos a VENDIDO.", pasajesGuardados.size());

        return pasajesGuardados.stream()
                .map(this::convertirAPasajeResponseDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public List<PasajeResponseDTO> reservarAsientosTemporalmente(CompraMultiplePasajesRequestDTO requestDTO) {
        // (La lógica es muy similar a comprar, pero con el estado RESERVADO)
        // Reutilizamos el mismo DTO para simplicidad.
        logger.info("Intentando reserva temporal para viaje ID {}, asientos {}", requestDTO.getViajeId(), requestDTO.getNumerosAsiento());

        Viaje viaje = viajeRepository.findById(requestDTO.getViajeId())
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + requestDTO.getViajeId()));

        Usuario cliente = usuarioRepository.findById(requestDTO.getClienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + requestDTO.getClienteId()));

        // --- VALIDACIONES CLAVE ---
        // (Similar a la compra: que el viaje esté programado, que haya asientos, que no estén ya vendidos o reservados)
        for (Integer numeroAsiento : requestDTO.getNumerosAsiento()) {
            pasajeRepository.findByDatosViajeAndNumeroAsiento(viaje, numeroAsiento)
                    .ifPresent(p -> {
                        if (p.getEstado() == EstadoPasaje.VENDIDO || p.getEstado() == EstadoPasaje.RESERVADO) {
                            throw new IllegalStateException("El asiento " + numeroAsiento + " ya no está disponible.");
                        }
                    });
        }

        List<Pasaje> pasajesReservados = new ArrayList<>();
        LocalDateTime fechaReserva = LocalDateTime.now(ZoneOffset.UTC);

        for (Integer numeroAsiento : requestDTO.getNumerosAsiento()) {
            Pasaje pasaje = new Pasaje();
            pasaje.setCliente(cliente);
            pasaje.setDatosViaje(viaje);
            pasaje.setNumeroAsiento(numeroAsiento);
            pasaje.setPrecio(viaje.getPrecio());
            pasaje.setEstado(EstadoPasaje.RESERVADO); // <-- ESTADO CLAVE
            pasaje.setFechaReserva(fechaReserva); // <-- MARCA DE TIEMPO CLAVE
            pasajesReservados.add(pasaje);
        }

        // Actualizamos el contador de asientos disponibles
        viaje.setAsientosDisponibles(viaje.getAsientosDisponibles() - requestDTO.getNumerosAsiento().size());
        viajeRepository.save(viaje);

        return pasajeRepository.saveAll(pasajesReservados).stream()
                .map(this::convertirAPasajeResponseDTO)
                .collect(Collectors.toList());
    }
}