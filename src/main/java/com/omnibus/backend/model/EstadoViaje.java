// src/main/java/com/omnibus/backend/model/EstadoViaje.java
package com.omnibus.backend.model;

public enum EstadoViaje {
    PROGRAMADO,     // El viaje está planificado pero aún no ha comenzado
    EN_CURSO,       // El viaje está actualmente en progreso (opcional, pero bueno para más detalle)
    VENTAS_CERRADAS,
    FINALIZADO,     // El viaje ha concluido
    CANCELADO       // El viaje fue cancelado
}