package com.omnibus.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync; // IMPORTANTE
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync // HABILITAR PROCESOS ASÍNCRONOS
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

}