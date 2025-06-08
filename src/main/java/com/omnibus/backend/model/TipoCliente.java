// src/main/java/com/omnibus/backend/model/TipoCliente.java
package com.omnibus.backend.model;

/**
 * Enumerado para definir los diferentes tipos de cliente.
 * COMUN: Cliente sin beneficios especiales.
 * JUBILADO: Cliente que puede acceder a descuentos por ser jubilado.
 * ESTUDIANTE: Cliente que puede acceder a descuentos por ser estudiante.
 */
public enum TipoCliente {
    COMUN,
    JUBILADO,
    ESTUDIANTE
}