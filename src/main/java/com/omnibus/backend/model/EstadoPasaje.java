// src/main/java/com/omnibus/backend/model/EstadoPasaje.java
package com.omnibus.backend.model;

public enum EstadoPasaje {
    VENDIDO,    // El pasaje está confirmado y pagado
    RESERVADO,  // El pasaje está reservado pero pendiente de pago/confirmación
    CANCELADO,  // El pasaje ha sido cancelado
    UTILIZADO   // El pasaje ya fue usado para viajar
    // Puedes añadir más estados según tu lógica de negocio
}