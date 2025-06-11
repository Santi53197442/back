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

import com.fasterxml.jackson.databind.JsonNode;

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

    // --- DECLARACIÓN DE DEPENDENCIAS ---
    private final PasajeRepository pasajeRepository;
    private final ViajeRepository viajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final PaypalService paypalService;
    private final PrecioService precioService;

    // --- CONSTRUCTOR ÚNICO Y CORREGIDO ---
    // Spring usará este constructor para inyectar TODAS las dependencias necesarias.
    @Autowired
    public pasajeService(PasajeRepository pasajeRepository,
                         ViajeRepository viajeRepository,
                         UsuarioRepository usuarioRepository,
                         PaypalService paypalService,
                         PrecioService precioService) {
        this.pasajeRepository = pasajeRepository;
        this.viajeRepository = viajeRepository;
        this.usuarioRepository = usuarioRepository;
        this.paypalService = paypalService;
        this.precioService = precioService;
    }

    @Transactional
    public PasajeResponseDTO comprarPasaje(CompraPasajeRequestDTO requestDTO) {
        logger.info("Intentando comprar pasaje para viaje ID {} por cliente ID {} en asiento {}",
                requestDTO.getViajeId(), requestDTO.getClienteId(), requestDTO.getNumeroAsiento());

        Viaje viaje = viajeRepository.findById(requestDTO.getViajeId())
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + requestDTO.getViajeId()));

        Usuario cliente = usuarioRepository.findById(requestDTO.getClienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + requestDTO.getClienteId()));

        // --- BLOQUE DE VALIDACIÓN ---
        if (viaje.getEstado() != EstadoViaje.PROGRAMADO) {
            throw new IllegalStateException("Solo se pueden comprar pasajes para viajes en estado PROGRAMADO.");
        }
        if (viaje.getAsientosDisponibles() <= 0) {
            throw new IllegalStateException("No hay asientos disponibles para el viaje ID: " + viaje.getId());
        }
        Omnibus busAsignado = viaje.getBusAsignado();
        if (busAsignado == null) {
            throw new IllegalStateException("El viaje ID " + viaje.getId() + " no tiene un ómnibus asignado.");
        }
        if (requestDTO.getNumeroAsiento() > busAsignado.getCapacidadAsientos() || requestDTO.getNumeroAsiento() < 1) {
            throw new IllegalArgumentException("Número de asiento " + requestDTO.getNumeroAsiento() + " es inválido.");
        }

        // --- CORRECCIÓN DE LA LÓGICA DE BÚSQUEDA ---
        List<EstadoPasaje> estadosActivos = List.of(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);

        pasajeRepository.findByDatosViajeAndNumeroAsientoAndEstadoIn(viaje, requestDTO.getNumeroAsiento(), estadosActivos)
                .ifPresent(pasajeExistente -> {
                    String mensajeError = "El asiento " + requestDTO.getNumeroAsiento() +
                            " ya está ocupado (estado: " + pasajeExistente.getEstado() +
                            ") para el viaje ID: " + viaje.getId();
                    logger.warn(mensajeError);
                    throw new IllegalStateException(mensajeError);
                });

        logger.info("Asiento {} disponible. Procediendo a crear pasaje...", requestDTO.getNumeroAsiento());

        // --- CREACIÓN DEL NUEVO PASAJE ---
        Pasaje nuevoPasaje = new Pasaje();
        nuevoPasaje.setCliente(cliente);
        nuevoPasaje.setDatosViaje(viaje);
        nuevoPasaje.setNumeroAsiento(requestDTO.getNumeroAsiento());
        nuevoPasaje.setPrecio(viaje.getPrecio()); // Ojo: Este método no usa el PrecioService con descuentos.
        nuevoPasaje.setEstado(EstadoPasaje.VENDIDO);
        // Nota: Aquí también deberías guardar el paypalTransactionId si este método se usa con PayPal.
        // Asumiendo que `CompraPasajeRequestDTO` tiene el campo `paypalTransactionId`.
        // nuevoPasaje.setPaypalTransactionId(requestDTO.getPaypalTransactionId());

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
        List<EstadoPasaje> estadosActivos = List.of(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);

        for (Integer numeroAsiento : requestDTO.getNumerosAsiento()) {
            Pasaje pasaje = pasajeRepository.findByDatosViajeAndNumeroAsientoAndEstado(viaje, numeroAsiento, EstadoPasaje.RESERVADO)
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
            // Se asigna el mismo ID de transacción a todos los pasajes de esta compra múltiple.
            pasaje.setPaypalTransactionId(requestDTO.getPaypalTransactionId());

            pasajesAConfirmar.add(pasaje);
        }

        List<Pasaje> pasajesGuardados = pasajeRepository.saveAll(pasajesAConfirmar);
        logger.info("¡Éxito! {} pasajes confirmados y movidos a VENDIDO.", pasajesGuardados.size());

        return pasajesGuardados.stream()
                .map(this::convertirAPasajeResponseDTO)
                .collect(Collectors.toList());
    }

    // En tu archivo PasajeService.java

    @Transactional
    public List<PasajeResponseDTO> reservarAsientosTemporalmente(CompraMultiplePasajesRequestDTO requestDTO) {
        logger.info("Intentando reserva temporal para viaje ID {}, cliente ID {}, asientos {}",
                requestDTO.getViajeId(), requestDTO.getClienteId(), requestDTO.getNumerosAsiento());

        // 1. Obtener las entidades necesarias (SIN CAMBIOS)
        Viaje viaje = viajeRepository.findById(requestDTO.getViajeId())
                .orElseThrow(() -> new EntityNotFoundException("Viaje no encontrado con ID: " + requestDTO.getViajeId()));

        Usuario cliente = usuarioRepository.findById(requestDTO.getClienteId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente no encontrado con ID: " + requestDTO.getClienteId()));


        // --- INICIO: LÓGICA DE VALIDACIÓN DEL LÍMITE DE RESERVAS (ÚNICO CÓDIGO AÑADIDO) ---
        final int MAX_ASIENTOS_TEMPORALES = 4;
        int nuevosAsientosCount = requestDTO.getNumerosAsiento().size();

        // Contar cuántos asientos ya tiene este cliente en estado RESERVADO para este viaje.
        // (Asegúrate de que 'countByViajeAndClienteAndEstado' existe en tu PasajeRepository)
        long asientosYaReservados = pasajeRepository.countByDatosViajeAndClienteAndEstado(viaje, cliente, EstadoPasaje.RESERVADO);

        // Verificar si la suma de los asientos ya reservados temporalmente y los nuevos supera el límite.
        if (asientosYaReservados + nuevosAsientosCount > MAX_ASIENTOS_TEMPORALES) {
            throw new IllegalStateException(
                    "Usted ya tiene " + asientosYaReservados + " asientos en una reserva temporal. " +
                            "No puede añadir " + nuevosAsientosCount + " más, ya que superaría el límite de " +
                            MAX_ASIENTOS_TEMPORALES + " asientos temporales por viaje."
            );
        }
        // --- FIN: LÓGICA DE VALIDACIÓN DEL LÍMITE DE RESERVAS ---


        // 2. Validar que los asientos no estén ya ocupados (SIN CAMBIOS)
        List<EstadoPasaje> estadosActivos = List.of(EstadoPasaje.VENDIDO, EstadoPasaje.RESERVADO);

        for (Integer numeroAsiento : requestDTO.getNumerosAsiento()) {
            pasajeRepository.findByDatosViajeAndNumeroAsientoAndEstadoIn(viaje, numeroAsiento, estadosActivos)
                    .ifPresent(p -> {
                        throw new IllegalStateException("El asiento " + numeroAsiento + " ya no está disponible.");
                    });
        }

        // 3. Calcular el precio final usando el PrecioService (SIN CAMBIOS)
        double precioFinalConDescuento = precioService.calcularPrecioFinal(viaje.getPrecio(), cliente);
        logger.info("Precio base: ${}. Precio final con descuento para cliente {}: ${}", viaje.getPrecio(), cliente.getId(), precioFinalConDescuento);

        // 4. Crear los pasajes con el estado RESERVADO y el precio final (SIN CAMBIOS)
        List<Pasaje> pasajesReservados = new ArrayList<>();
        LocalDateTime fechaReserva = LocalDateTime.now(ZoneOffset.UTC);

        for (Integer numeroAsiento : requestDTO.getNumerosAsiento()) {
            Pasaje pasaje = new Pasaje();
            pasaje.setCliente(cliente);
            pasaje.setDatosViaje(viaje); // Asumo que se llama setViaje o similar
            pasaje.setNumeroAsiento(numeroAsiento);
            pasaje.setEstado(EstadoPasaje.RESERVADO);
            pasaje.setFechaReserva(fechaReserva);
            pasaje.setPrecio(precioFinalConDescuento);
            pasajesReservados.add(pasaje);
        }

        // 5. Actualizar el contador de asientos del viaje y guardar los pasajes (SIN CAMBIOS)
        viaje.setAsientosDisponibles(viaje.getAsientosDisponibles() - requestDTO.getNumerosAsiento().size());
        viajeRepository.save(viaje);

        List<Pasaje> pasajesGuardados = pasajeRepository.saveAll(pasajesReservados);

        // 6. Devolver los DTOs (SIN CAMBIOS)
        return pasajesGuardados.stream()
                .map(this::convertirAPasajeResponseDTO)
                .collect(Collectors.toList());
    }


    @Transactional
    public String procesarDevolucionPasaje(Integer pasajeId) {
        logger.info("Iniciando proceso de devolución para pasaje ID: {}", pasajeId);

        // 1. Obtener el pasaje
        Pasaje pasaje = pasajeRepository.findById(pasajeId)
                .orElseThrow(() -> new EntityNotFoundException("Pasaje no encontrado con ID: " + pasajeId));

        // 2. Validaciones de negocio
        if (pasaje.getEstado() != EstadoPasaje.VENDIDO) {
            throw new IllegalStateException("Solo se pueden devolver pasajes en estado 'VENDIDO'. Estado actual: " + pasaje.getEstado());
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaSalida = LocalDateTime.of(pasaje.getDatosViaje().getFecha(), pasaje.getDatosViaje().getHoraSalida());
        if (ahora.plusHours(24).isAfter(fechaSalida)) {
            throw new IllegalStateException("El plazo para la devolución ha expirado (se requieren 24hs de antelación).");
        }

        if (pasaje.getPaypalTransactionId() == null || pasaje.getPaypalTransactionId().isBlank()) {
            throw new IllegalStateException("El pasaje no tiene un ID de transacción de PayPal asociado. No se puede reembolsar.");
        }

        // 3. Calcular monto a reembolsar (con 10% de penalización)
        // ¡Correcto! Usamos el precio guardado en el pasaje, que ya incluye descuentos.
        double precioPagado = pasaje.getPrecio();
        double montoPenalizacion = precioPagado * 0.10;
        double montoAReembolsar = precioPagado - montoPenalizacion;

        // 4. Llamar a PayPal para el reembolso
        JsonNode refundResponse = paypalService.refundPayment(pasaje.getPaypalTransactionId(), montoAReembolsar);

        if (refundResponse == null || !"COMPLETED".equals(refundResponse.path("status").asText())) {
            String status = refundResponse != null ? refundResponse.path("status").asText() : "N/A";
            throw new RuntimeException("El reembolso en PayPal falló. Estado recibido: " + status);
        }

        String refundId = refundResponse.path("id").asText();

        // 5. Actualizar la base de datos
        pasaje.setEstado(EstadoPasaje.CANCELADO); // O un nuevo estado "DEVUELTO" si lo prefieres
        pasaje.setPaypalRefundId(refundId);

        Viaje viaje = pasaje.getDatosViaje();
        viaje.setAsientosDisponibles(viaje.getAsientosDisponibles() + 1);

        pasajeRepository.save(pasaje);
        viajeRepository.save(viaje);

        logger.info("Devolución exitosa para pasaje ID {}. Reembolsado: ${}. Nuevo estado: {}", pasajeId, montoAReembolsar, pasaje.getEstado());

        // Aquí podrías llamar al servicio de notificaciones por email
        // asyncService.sendRefundEmailAsync(pasaje, montoAReembolsar);

        return String.format(Locale.US, "Devolución procesada con éxito. Se reembolsó un total de $%.2f.", montoAReembolsar);
    }
    // --- NUEVO MÉTODO PARA BUSCAR UN PASAJE POR ID ---
    @Transactional(readOnly = true)
    public PasajeResponseDTO obtenerPasajePorId(Integer pasajeId) {
        logger.info("Buscando detalles del pasaje con ID: {}", pasajeId);
        Pasaje pasaje = pasajeRepository.findById(pasajeId)
                .orElseThrow(() -> {
                    logger.warn("No se encontró el pasaje con ID: {}", pasajeId);
                    return new EntityNotFoundException("Pasaje no encontrado con ID: " + pasajeId);
                });

        // Reutilizamos el método de conversión que ya tienes
        return convertirAPasajeResponseDTO(pasaje);
    }
}