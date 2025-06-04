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
    private String ci;       // CI como String, ya que Usuario.getCi() es Integer
    private String email;
    // Puedes añadir más campos si el frontend los necesita y están disponibles en Usuario/Cliente
}