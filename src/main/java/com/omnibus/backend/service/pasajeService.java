// src/main/java/com/omnibus/backend/service/PasajeService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.CompraPasajeRequestDTO;
import com.omnibus.backend.dto.PasajeResponseDTO;
import com.omnibus.backend.model.*; // EstadoPasaje, EstadoViaje, Pasaje, Usuario, Viaje, Localidad, Omnibus
import com.omnibus.backend.repository.PasajeRepository;
import com.omnibus.backend.repository.UsuarioRepository;
import com.omnibus.backend.repository.ViajeRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class pasajeService { // Nombre de clase corregido a PascalCase

    private static final Logger logger = LoggerFactory.getLogger(pasajeService.class); // Nombre de clase corregido

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

    @Transactional(readOnly = true)
    public List<Integer> obtenerAsientosOcupados(Integer viajeId) {
        logger.debug("Solicitando asientos ocupados para viaje ID: {}", viajeId);
        if (!viajeRepository.existsById(viajeId)) {
            logger.warn("Intento de obtener asientos de viaje inexistente ID: {}", viajeId);
            throw new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId);
        }

        List<Pasaje> pasajesDelViaje = pasajeRepository.findByDatosViajeId(viajeId);
        List<Integer> asientosOcupados = pasajesDelViaje.stream()
                .filter(p -> p.getEstado() != EstadoPasaje.CANCELADO)
                .map(Pasaje::getNumeroAsiento)
                .distinct()
                .collect(Collectors.toList());
        logger.debug("Asientos ocupados para viaje ID {}: {}", viajeId, asientosOcupados);
        return asientosOcupados;
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

    private PasajeResponseDTO convertirAPasajeResponseDTO(Pasaje pasaje) {
        if (pasaje == null) return null;

        Long idCliente = null;
        String nombreCliente = "Cliente Desconocido";
        if (pasaje.getCliente() != null) {
            idCliente = pasaje.getCliente().getId();
            nombreCliente = pasaje.getCliente().getNombre() != null ? pasaje.getCliente().getNombre() : "Nombre no disponible";
        }

        Integer idViaje = null;
        String origenNombre = "Origen Desconocido";
        String destinoNombre = "Destino Desconocido";
        LocalDate fechaViaje = null;
        LocalTime horaSalidaViaje = null;

        if (pasaje.getDatosViaje() != null) {
            idViaje = pasaje.getDatosViaje().getId();
            fechaViaje = pasaje.getDatosViaje().getFecha();
            horaSalidaViaje = pasaje.getDatosViaje().getHoraSalida();

            if (pasaje.getDatosViaje().getOrigen() != null) {
                origenNombre = pasaje.getDatosViaje().getOrigen().getNombre() != null ? pasaje.getDatosViaje().getOrigen().getNombre() : "Origen no especificado";
            }
            if (pasaje.getDatosViaje().getDestino() != null) {
                destinoNombre = pasaje.getDatosViaje().getDestino().getNombre() != null ? pasaje.getDatosViaje().getDestino().getNombre() : "Destino no especificado";
            }
        }

        Float precio = pasaje.getPrecio() != null ? pasaje.getPrecio().floatValue() : null;

        return new PasajeResponseDTO(
                pasaje.getId(),
                idCliente,
                nombreCliente,
                idViaje,
                origenNombre,
                destinoNombre,
                fechaViaje,
                horaSalidaViaje,
                precio,
                pasaje.getEstado(),
                pasaje.getNumeroAsiento()
        );
    }
}