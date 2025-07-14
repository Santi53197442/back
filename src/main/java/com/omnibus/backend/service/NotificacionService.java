package com.omnibus.backend.service;

import com.omnibus.backend.model.Notificacion;
import com.omnibus.backend.model.Pasaje;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.repository.NotificacionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class NotificacionService {

    @Autowired
    private NotificacionRepository notificacionRepository;

    public void crearNotificacionRecordatorioViaje(Pasaje pasaje) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm");
        String fechaHoraFormateada = pasaje.getDatosViaje().getFechaHoraSalida().format(formatter);

        String mensaje = String.format(
                "Recordatorio: Tu viaje a %s sale pronto. Fecha: %s.",
                pasaje.getDatosViaje().getDestino().getNombre(),
                fechaHoraFormateada
        );

        Notificacion notificacion = Notificacion.builder()
                .usuario(pasaje.getCliente())
                .mensaje(mensaje)
                .fechaCreacion(LocalDateTime.now())
                .leida(false)
                // Opcional: puedes agregar un link a la página de "Mis Viajes"
                // .link("/mis-viajes/" + pasaje.getId())
                .build();

        notificacionRepository.save(notificacion);
    }
}