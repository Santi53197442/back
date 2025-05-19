package com.omnibus.backend.controller; // Mismo paquete que AuthController

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

    @GetMapping("/") // Este método manejará las solicitudes a la raíz de tu aplicación
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Application is up and running!");
    }
}
