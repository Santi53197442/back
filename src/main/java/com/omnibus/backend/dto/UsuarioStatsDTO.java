// src/main/java/com/omnibus/backend/dto/UsuarioStatsDTO.java
package com.omnibus.backend.dto;

import com.omnibus.backend.model.TipoCliente;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioStatsDTO {
    private String rol;
    private LocalDateTime fechaCreacion;
    private TipoCliente tipoCliente; // Será nulo si el usuario no es un Cliente
}