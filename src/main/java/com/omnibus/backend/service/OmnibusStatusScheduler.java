// src/main/java/com/omnibus/backend/service/OmnibusStatusScheduler.java
package com.omnibus.backend.service;

import com.omnibus.backend.model.EstadoBus;
import com.omnibus.backend.model.Omnibus;
import com.omnibus.backend.repository.OmnibusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class OmnibusStatusScheduler {

    private static final Logger logger = LoggerFactory.getLogger(OmnibusStatusScheduler.class);
    private static final ZoneId ZONA_HORARIA_URUGUAY = ZoneId.of("America/Montevideo");

    private final OmnibusRepository omnibusRepository;

    @Autowired
    public OmnibusStatusScheduler(OmnibusRepository omnibusRepository) {
        this.omnibusRepository = omnibusRepository;
    }

    @Scheduled(cron = "0 * * * * *") // Se ejecuta cada minuto
    @Transactional
    public void actualizarEstadosDeOmnibus() {
        LocalDateTime ahora = LocalDateTime.now(ZONA_HORARIA_URUGUAY);

        // 1. Iniciar inactividades programadas
        iniciarInactividades(ahora);

        // 2. Finalizar inactividades y volver a OPERATIVO
        finalizarInactividades(ahora);
    }

    private void iniciarInactividades(LocalDateTime ahora) {
        List<Omnibus> omnibusParaInactivar = omnibusRepository.findByEstadoAndInicioInactividadProgramadaBefore(EstadoBus.OPERATIVO, ahora);

        if (!omnibusParaInactivar.isEmpty()) {
            logger.info("[!] Encontrados {} ómnibus para iniciar su período de inactividad programada.", omnibusParaInactivar.size());
            for (Omnibus omnibus : omnibusParaInactivar) {
                if (omnibus.getEstadoProgramado() != null && omnibus.getInicioInactividadProgramada() != null) {
                    logger.info("--> Cambiando estado del ómnibus {} de OPERATIVO a {}.", omnibus.getMatricula(), omnibus.getEstadoProgramado());
                    omnibus.setEstado(omnibus.getEstadoProgramado());
                    // Limpiamos solo el estado programado y la fecha de inicio para saber que ya empezó
                    omnibus.setEstadoProgramado(null);
                    omnibus.setInicioInactividadProgramada(null);
                }
            }
            omnibusRepository.saveAll(omnibusParaInactivar);
        }
    }

    private void finalizarInactividades(LocalDateTime ahora) {
        // Busca buses que están inactivos y cuya fecha de fin de inactividad ya pasó
        List<EstadoBus> estadosInactivos = List.of(EstadoBus.EN_MANTENIMIENTO, EstadoBus.FUERA_DE_SERVICIO);
        List<Omnibus> omnibusParaReactivar = omnibusRepository.findByEstadoInAndFinInactividadProgramadaBefore(estadosInactivos, ahora);

        if (!omnibusParaReactivar.isEmpty()) {
            logger.info("[!] Encontrados {} ómnibus para finalizar su período de inactividad.", omnibusParaReactivar.size());
            for (Omnibus omnibus : omnibusParaReactivar) {
                logger.info("--> Reactivando ómnibus {}. Volviendo a estado OPERATIVO.", omnibus.getMatricula());
                omnibus.setEstado(EstadoBus.OPERATIVO);
                // Limpiamos el campo de fin de inactividad para que no se vuelva a procesar
                omnibus.setFinInactividadProgramada(null);
            }
            omnibusRepository.saveAll(omnibusParaReactivar);
        }
    }
}