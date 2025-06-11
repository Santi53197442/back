// src/main/java/com/omnibus/backend/repository/specification/UsuarioSpecification.java
package com.omnibus.backend.repository.specification;

import com.omnibus.backend.model.Administrador;
import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.Usuario;
import com.omnibus.backend.model.Vendedor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils; // Utilidad de Spring para chequear strings

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class UsuarioSpecification {

    public static Specification<Usuario> withFilters(String nombre, String email, String rol) {
        // La expresión lambda (root, query, builder) es la implementación del método toPredicate
        return (root, query, builder) -> {

            // Creamos una lista para almacenar todas nuestras condiciones (predicados)
            List<Predicate> predicates = new ArrayList<>();

            // 1. Filtro por Nombre o Apellido (case-insensitive)
            if (StringUtils.hasText(nombre)) {
                // El frontend busca en "nombre + apellido", así que aquí replicamos esa lógica con un OR
                Predicate nombrePredicate = builder.like(builder.lower(root.get("nombre")), "%" + nombre.toLowerCase() + "%");
                Predicate apellidoPredicate = builder.like(builder.lower(root.get("apellido")), "%" + nombre.toLowerCase() + "%");
                predicates.add(builder.or(nombrePredicate, apellidoPredicate));
            }

            // 2. Filtro por Email (case-insensitive)
            if (StringUtils.hasText(email)) {
                predicates.add(builder.like(builder.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }

            // 3. Filtro por Rol
            // Este es el más complejo porque el rol depende de la subclase
            if (StringUtils.hasText(rol)) {
                // Usamos root.type() para obtener la clase de la entidad (Administrador, Vendedor, etc.)
                switch (rol.toUpperCase()) {
                    case "ADMINISTRADOR":
                        predicates.add(builder.equal(root.type(), Administrador.class));
                        break;
                    case "VENDEDOR":
                        predicates.add(builder.equal(root.type(), Vendedor.class));
                        break;
                    case "CLIENTE":
                        predicates.add(builder.equal(root.type(), Cliente.class));
                        break;
                    // Puedes añadir un caso default o ignorar roles no conocidos
                }
            }

            // Combinamos todos los predicados con un AND.
            // Si la lista de predicados está vacía, devuelve un predicado que es siempre verdadero (no filtra nada).
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}