package com.bancoxyz.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Lectura tolerante de montos, saldos y edades del sistema legacy. */
class ParseadorNumerosTest {

    @Test
    @DisplayName("Convierte montos validos, incluidos negativos y con separadores")
    void parseaMontos() {
        assertThat(ParseadorNumeros.parsearMonto("1000")).contains(new BigDecimal("1000"));
        assertThat(ParseadorNumeros.parsearMonto(" -200 ")).contains(new BigDecimal("-200"));
        assertThat(ParseadorNumeros.parsearMonto("1.500,50")).contains(new BigDecimal("1500.50"));
        assertThat(ParseadorNumeros.parsearMonto("$3000")).contains(new BigDecimal("3000"));
    }

    @Test
    @DisplayName("Un monto vacio o no numerico no se inventa: devuelve vacio")
    void rechazaMontosInvalidos() {
        assertThat(ParseadorNumeros.parsearMonto("")).isEmpty();
        assertThat(ParseadorNumeros.parsearMonto(null)).isEmpty();
        assertThat(ParseadorNumeros.parsearMonto("N/A")).isEmpty();
    }

    @Test
    @DisplayName("Convierte enteros y enteros largos")
    void parseaEnteros() {
        assertThat(ParseadorNumeros.parsearEntero("30")).contains(30);
        assertThat(ParseadorNumeros.parsearEntero(" 45 ")).contains(45);
        assertThat(ParseadorNumeros.parsearEntero("")).isEmpty();
        assertThat(ParseadorNumeros.parsearEnteroLargo("101")).contains(101L);
        assertThat(ParseadorNumeros.parsearEnteroLargo("x")).isEmpty();
    }

    @Test
    @DisplayName("Normaliza los valores de catalogo recortando y bajando a minusculas")
    void normalizaTexto() {
        assertThat(ParseadorNumeros.normalizarTexto("  DEBITO ")).isEqualTo("debito");
        assertThat(ParseadorNumeros.normalizarTexto(null)).isEmpty();
    }
}
