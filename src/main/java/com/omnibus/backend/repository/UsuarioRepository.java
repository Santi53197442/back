// src/main/java/com/omnibus/backend/repository/UsuarioRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor; // <-- 1. Asegúrate de que este import está presente.
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
// 2. LA LÍNEA MÁS IMPORTANTE: DEBE EXTENDER AMBAS INTERFACES.
public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {

    // El resto de tus métodos personalizados no cambian
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByResetPasswordToken(String token);
    Optional<Usuario> findByCi(Integer ci);
}