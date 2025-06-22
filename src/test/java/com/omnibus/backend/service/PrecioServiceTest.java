package com.omnibus.backend.service;

import com.omnibus.backend.model.Cliente;
import com.omnibus.backend.model.TipoCliente;
import com.omnibus.backend.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class PrecioServiceTest {

    private PrecioService precioService;

    @BeforeEach
    void setUp() {
        precioService = new PrecioService();
    }

    @Test
    void calcularPrecioFinal_usuarioNoCliente_devuelvePrecioBase() {
        // En lugar de new Usuario()… mockeamos un Usuario sin necesidad de implementar nada, de lo contrario da error
        Usuario usuarioGenerico = Mockito.mock(Usuario.class);
        double precioBase = 100.0;

        double resultado = precioService.calcularPrecioFinal(precioBase, usuarioGenerico);

        assertEquals(precioBase, resultado,
                "Para usuarios no Clientes debe devolver el precio base sin descuento");
    }

    @Test
    void calcularPrecioFinal_clienteComun_devuelvePrecioBase() {
        Cliente clienteComun = new Cliente();
        clienteComun.setTipo(TipoCliente.COMUN);
        double precioBase = 150.0;

        double resultado = precioService.calcularPrecioFinal(precioBase, clienteComun);

        assertEquals(precioBase, resultado,
                "Cliente de tipo COMUN no es elegible para descuento, debe devolver precio base");
    }

    @Test
    void calcularPrecioFinal_clienteJubilado_aplicaDescuento20PorCiento() {
        Cliente clienteJubilado = new Cliente();
        clienteJubilado.setTipo(TipoCliente.JUBILADO);
        double precioBase = 200.0;
        double esperado = 200.0 * (1 - 0.20); // 160.0

        double resultado = precioService.calcularPrecioFinal(precioBase, clienteJubilado);

        assertEquals(esperado, resultado, 1e-6,
                "Cliente JUBILADO debe recibir un 20% de descuento");
    }

    @Test
    void calcularPrecioFinal_clienteEstudiante_aplicaDescuento20PorCiento() {
        Cliente clienteEstudiante = new Cliente();
        clienteEstudiante.setTipo(TipoCliente.ESTUDIANTE);
        double precioBase = 80.0;
        double esperado = 80.0 * (1 - 0.20); // 64.0

        double resultado = precioService.calcularPrecioFinal(precioBase, clienteEstudiante);

        assertEquals(esperado, resultado, 1e-6,
                "Cliente ESTUDIANTE debe recibir un 20% de descuento");
    }
}
