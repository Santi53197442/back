package com.omnibus.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponseDTO {
    private String token;
    private String email;
    private String rol;
    // Puedes añadir más info del usuario si es necesario
}