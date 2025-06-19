package com.omnibus.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@AllArgsConstructor
public class RegisterDTO {
    public String nombre;
    public String apellido;
    public Integer ci;
    public String contrasenia;
    public String email;
    public Integer telefono;
    public LocalDate fechaNac;
    public String rol;


}
