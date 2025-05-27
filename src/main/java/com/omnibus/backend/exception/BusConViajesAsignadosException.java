// src/main/java/com/omnibus/backend/exception/BusConViajesAsignadosException.java
package com.omnibus.backend.exception;

import com.omnibus.backend.model.Viaje;
import lombok.Getter;
import java.util.List;

@Getter
public class BusConViajesAsignadosException extends RuntimeException {
    private final List<Viaje> viajesConflictivos;

    public BusConViajesAsignadosException(String message, List<Viaje> viajesConflictivos) {
        super(message);
        this.viajesConflictivos = viajesConflictivos;
    }

    public BusConViajesAsignadosException(String message) {
        super(message);
        this.viajesConflictivos = null; // O una lista vacía
    }
}

