// src/main/java/com/omnibus/backend/repository/UsuarioRepository.java
package com.omnibus.backend.repository;

import com.omnibus.backend.model.Usuario; // Importa la clase base Usuario
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> { // Se mantiene Usuario
    Optional<Usuario> findByEmail(String email); // Devolverá instancias de Usuario (Cliente, Vendedor, etc.)
    Optional<Usuario> findByResetPasswordToken(String token);
    Optional<Usuario> findByCi(String ci);

}