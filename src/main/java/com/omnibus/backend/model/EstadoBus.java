package com.omnibus.backend.model;

public enum EstadoBus {
    OPERATIVO,      // En funcionamiento normal
    EN_MANTENIMIENTO, // Fuera de servicio por mantenimiento
    FUERA_DE_SERVICIO, // No operativo por otras razones (avería grave, etc.)
    ASIGNADO_A_VIAJE // Actualmente asignado o realizando un viaje
    // Puedes añadir más estados según necesites
}