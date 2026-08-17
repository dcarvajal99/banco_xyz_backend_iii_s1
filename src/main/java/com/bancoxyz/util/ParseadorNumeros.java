package com.bancoxyz.util;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Lectura tolerante de los campos numericos del sistema legacy.
 *
 * <p>Los montos y saldos llegan como texto y pueden venir vacios, con espacios de relleno
 * o con separadores de miles heredados del reporte COBOL. Se usa {@link BigDecimal} y no
 * {@code double} porque son importes de dinero: la aritmetica binaria de coma flotante
 * introduce diferencias de centavos al sumar miles de filas.</p>
 */
public final class ParseadorNumeros {

    private ParseadorNumeros() {
        // Clase de utilidad: no se instancia.
    }

    /**
     * Convierte un importe legacy en {@link BigDecimal}.
     *
     * @param valor texto crudo del CSV
     * @return el importe, o {@link Optional#empty()} si viene vacio o no es numerico
     */
    public static Optional<BigDecimal> parsearMonto(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        String limpio = valor.trim().replace(".", "").replace(",", ".").replace("$", "").replace(" ", "");
        try {
            return Optional.of(new BigDecimal(limpio));
        } catch (NumberFormatException ignorada) {
            return Optional.empty();
        }
    }

    /**
     * Convierte un campo entero legacy (edad, identificador de cuenta).
     *
     * @param valor texto crudo del CSV
     * @return el entero, o {@link Optional#empty()} si viene vacio o no es numerico
     */
    public static Optional<Integer> parsearEntero(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(valor.trim()));
        } catch (NumberFormatException ignorada) {
            return Optional.empty();
        }
    }

    /**
     * Convierte un campo entero largo legacy (identificador de transaccion).
     *
     * @param valor texto crudo del CSV
     * @return el entero largo, o {@link Optional#empty()} si viene vacio o no es numerico
     */
    public static Optional<Long> parsearEnteroLargo(String valor) {
        if (valor == null || valor.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(valor.trim()));
        } catch (NumberFormatException ignorada) {
            return Optional.empty();
        }
    }

    /** Normaliza un texto de catalogo: recorta espacios y lo baja a minusculas. */
    public static String normalizarTexto(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase();
    }
}
