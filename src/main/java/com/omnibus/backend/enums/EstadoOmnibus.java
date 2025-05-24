package com.omnibus.backend.enums;

public enum EstadoOmnibus {
    ACTIVO,         // Disponible para viajes
    INACTIVO,       // No disponible temporalmente (ej. fin de vida útil antes de baja definitiva)
    MANTENIMIENTO,  // En taller o revisión
    EN_VIAJE        // Actualmente en ruta
}
