// src/main/java/com/omnibus/backend/service/PasajeService.java
package com.omnibus.backend.service;

import com.omnibus.backend.dto.CompraPasajeRequestDTO;
import com.omnibus.backend.dto.PasajeResponseDTO;
import com.omnibus.backend.model.*; // EstadoPasaje, EstadoViaje, Pasaje, Usuario, Viaje
import com.omnibus.backend.repository.PasajeRepository;
import com.omnibus.backend.repository.UsuarioRepository; // Necesitas este repositorio
import com.omnibus.backend.repository.ViajeRepository;   // Necesitas este repositorio
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class pasajeService {

    private static final Logger logger = LoggerFactory.getLogger(pasajeService.class);

    private final PasajeRepository pasajeRepository;
    private final ViajeRepository viajeRepository;
    private final UsuarioRepository usuarioRepository;
    // private final PdfService pdfService; // Si implementas generación de PDF

    @Autowired
    public pasajeService(PasajeRepository pasajeRepository,
                         ViajeRepository viajeRepository,
                         UsuarioRepository usuarioRepository
            /*, PdfService pdfService */) {
        this.pasajeRepository = pasajeRepository;
        this.viajeRepository = viajeRepository;
        this.usuarioRepository = usuarioRepository;
        // this.pdfService = pdfService;
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

        // 1. Verificar si el viaje está en estado PROGRAMADO (o el estado que permitas para compra)
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO) {
            String mensajeError = "Solo se pueden comprar pasajes para viajes en estado PROGRAMADO. Estado actual: " + viaje.getEstado();
            logger.warn(mensajeError + " para viaje ID {}", viaje.getId());
            throw new IllegalStateException(mensajeError);
        }

        // 2. Verificar asientos disponibles en el viaje (conteo general)
        if (viaje.getAsientosDisponibles() <= 0) {
            String mensajeError = "No hay asientos disponibles (conteo general) para el viaje ID: " + viaje.getId();
            logger.warn(mensajeError);
            throw new IllegalStateException(mensajeError);
        }

        // 3. Verificar que el número de asiento sea válido para la capacidad del bus
        if (requestDTO.getNumeroAsiento() > viaje.getBusAsignado().getCapacidadAsientos() || requestDTO.getNumeroAsiento() < 1) {
            String mensajeError = "Número de asiento " + requestDTO.getNumeroAsiento() +
                    " inválido para un ómnibus con capacidad " + viaje.getBusAsignado().getCapacidadAsientos() + " asientos.";
            logger.warn(mensajeError + " para viaje ID {}", viaje.getId());
            throw new IllegalArgumentException(mensajeError);
        }

        // 4. Verificar si el asiento específico ya está ocupado (VENDIDO, RESERVADO, UTILIZADO)
        pasajeRepository.findByDatosViajeAndNumeroAsiento(viaje, requestDTO.getNumeroAsiento())
                .ifPresent(pasajeExistente -> {
                    if (pasajeExistente.getEstado() != EstadoPasaje.CANCELADO) {
                        String mensajeError = "El asiento " + requestDTO.getNumeroAsiento() +
                                " ya está ocupado (estado: " + pasajeExistente.getEstado() +
                                ") para el viaje ID: " + viaje.getId();
                        logger.warn(mensajeError);
                        throw new IllegalStateException(mensajeError);
                    }
                    // Si está CANCELADO, se puede sobrescribir/reutilizar, aunque esto es raro.
                    // Generalmente, no se crea un nuevo pasaje, sino que se actualiza el cancelado o se maneja de otra forma.
                    // Para este caso, asumimos que un asiento con pasaje CANCELADO está "libre" para una nueva venta.
                });

        // Simulación de pago (Aquí iría la integración con PayPal, etc.)
        // Por ahora, asumimos que el pago es exitoso.
        logger.info("Simulando proceso de pago para viaje ID {} asiento {}...", viaje.getId(), requestDTO.getNumeroAsiento());
        // pagoExitoso = simularPago();
        // if (!pagoExitoso) { throw new RuntimeException("Falló el pago"); }

        Pasaje nuevoPasaje = new Pasaje();
        nuevoPasaje.setCliente(cliente);
        nuevoPasaje.setDatosViaje(viaje);
        nuevoPasaje.setNumeroAsiento(requestDTO.getNumeroAsiento());
        nuevoPasaje.setPrecio(viaje.getPrecio()); // El precio del pasaje es el precio del viaje
        nuevoPasaje.setEstado(EstadoPasaje.VENDIDO); // O RESERVADO si tienes un flujo de pago posterior

        // Actualizar asientos disponibles en el viaje
        viaje.setAsientosDisponibles(viaje.getAsientosDisponibles() - 1);
        viajeRepository.save(viaje); // Guardar el viaje actualizado

        Pasaje pasajeGuardado = pasajeRepository.save(nuevoPasaje);
        logger.info("Pasaje ID {} creado exitosamente para viaje ID {} asiento {}, estado: VENDIDO",
                pasajeGuardado.getId(), viaje.getId(), pasajeGuardado.getNumeroAsiento());

        return convertirAPasajeResponseDTO(pasajeGuardado);
    }

    public List<Integer> obtenerAsientosOcupados(Integer viajeId) {
        logger.debug("Solicitando asientos ocupados para viaje ID: {}", viajeId);
        // No es estrictamente necesario cargar el viaje aquí si solo queremos los pasajes,
        // pero puede ser una validación útil que el viaje exista.
        if (!viajeRepository.existsById(viajeId)) {
            logger.warn("Intento de obtener asientos de viaje inexistente ID: {}", viajeId);
            throw new EntityNotFoundException("Viaje no encontrado con ID: " + viajeId);
        }

        List<Pasaje> pasajesDelViaje = pasajeRepository.findByDatosViajeId(viajeId);
        List<Integer> asientosOcupados = pasajesDelViaje.stream()
                .filter(p -> p.getEstado() != EstadoPasaje.CANCELADO) // Asientos VENDIDOS, RESERVADOS, UTILIZADOS se consideran ocupados
                .map(Pasaje::getNumeroAsiento)
                .distinct() // Asegurar que no haya duplicados si la lógica lo permitiera por error
                .collect(Collectors.toList());
        logger.debug("Asientos ocupados para viaje ID {}: {}", viajeId, asientosOcupados);
        return asientosOcupados;
    }

    // Helper para convertir Entidad a DTO (Idealmente usar MapStruct o similar)
    private PasajeResponseDTO convertirAPasajeResponseDTO(Pasaje pasaje) {
        if (pasaje == null) return null;
        return new PasajeResponseDTO(
                pasaje.getId(),
                pasaje.getCliente().getId(),
                pasaje.getCliente().getNombre(), // Asumiendo que Usuario tiene getNombre()
                pasaje.getDatosViaje().getId(),
                pasaje.getDatosViaje().getOrigen().getNombre(), // Asumiendo getNombre() en Localidad
                pasaje.getDatosViaje().getDestino().getNombre(),
                pasaje.getDatosViaje().getFecha(),
                pasaje.getDatosViaje().getHoraSalida(),
                pasaje.getPrecio() != null ? pasaje.getPrecio().floatValue() : null, // Convertir Double a Float para el DTO
                pasaje.getEstado(),
                pasaje.getNumeroAsiento()
        );
    }

    // --- Lógica para PDF (Ejemplo básico, si la necesitas más adelante) ---
    // public byte[] generarPdfPasaje(Integer pasajeId) {
    //     Pasaje pasaje = pasajeRepository.findById(pasajeId)
    //             .orElseThrow(() -> new EntityNotFoundException("Pasaje no encontrado: " + pasajeId));
    //     // return pdfService.crearPdfDesdePasaje(pasaje);
    //     logger.info("Generando PDF simulado para pasaje ID: {}", pasajeId);
    //     return ("Este es el PDF simulado para el pasaje ID: " + pasajeId).getBytes(); // Placeholder
    // }
}