package com.omnibus.backend.repository;

import com.omnibus.backend.model.Notificacion;
import com.omnibus.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // Para obtener todas las notificaciones de un usuario, ordenadas por fecha
    List<Notificacion> findByUsuarioOrderByFechaCreacionDesc(Usuario usuario);

    // Para contar cuántas notificaciones no leídas tiene un usuario
    long countByUsuarioAndLeidaIsFalse(Usuario usuario);
}