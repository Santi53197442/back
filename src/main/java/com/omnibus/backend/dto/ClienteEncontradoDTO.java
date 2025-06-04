// src/main/java/com/omnibus/backend/dto/ClienteEncontradoDTO.java
package com.omnibus.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClienteEncontradoDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String ci;
    private String email;
    // Puedes añadir más campos que quieras devolver al frontend sobre el cliente
}