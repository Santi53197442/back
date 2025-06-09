package com.omnibus.backend.service;

import com.omnibus.backend.model.Cliente; // Asegúrate de importar tu entidad Cliente
import com.omnibus.backend.model.TipoCliente;
import com.omnibus.backend.model.Usuario;
import org.springframework.stereotype.Service;

@Service
public class PrecioService {

    // Define la tasa de descuento como una constante
    private static final double TASA_DESCUENTO = 0.20; // 20%

    /**
     * Calcula el precio final de un pasaje aplicando descuentos si corresponde.
     * @param precioBase El precio original del viaje.
     * @param usuario El usuario que realiza la compra.
     * @return El precio final con el descuento aplicado.
     */
    public double calcularPrecioFinal(double precioBase, Usuario usuario) {
        // Verifica si el usuario es una instancia de Cliente (o tu entidad específica que tiene tipoCliente)
        if (usuario instanceof Cliente) {
            TipoCliente tipo = ((Cliente) usuario).getTipo();

            // Aplica el descuento si el tipo es JUBILADO o ESTUDIANTE
            if (tipo == TipoCliente.JUBILADO || tipo == TipoCliente.ESTUDIANTE) {
                double descuento = precioBase * TASA_DESCUENTO;
                return precioBase - descuento;
            }
        }

        // Si no es un cliente elegible, devuelve el precio base sin cambios
        return precioBase;
    }
}