package com.bancoxyz.batch.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cubre el caso que motiva la clase: distinguir un duplicado real de una fila que Spring
 * Batch vuelve a procesar despues de revertir un chunk.
 */
class DetectorDeDuplicadosTest {

    @Test
    @DisplayName("La primera aparicion de una clave no es duplicado")
    void primeraAparicionNoEsDuplicado() {
        DetectorDeDuplicados detector = new DetectorDeDuplicados();
        assertThat(detector.esDuplicado("101", 2)).isFalse();
    }

    @Test
    @DisplayName("La misma clave en otra linea si es duplicado")
    void mismaClaveEnOtraLineaEsDuplicado() {
        DetectorDeDuplicados detector = new DetectorDeDuplicados();
        detector.esDuplicado("101", 2);
        assertThat(detector.esDuplicado("101", 9)).isTrue();
    }

    @Test
    @DisplayName("Reprocesar la misma linea tras un rollback NO cuenta como duplicado")
    void reprocesarLaMismaLineaNoEsDuplicado() {
        DetectorDeDuplicados detector = new DetectorDeDuplicados();
        detector.esDuplicado("101", 2);

        // Spring Batch revierte el chunk y vuelve a pasar la fila 2 por el procesador.
        assertThat(detector.esDuplicado("101", 2)).isFalse();
        assertThat(detector.esDuplicado("101", 2)).isFalse();
    }

    @Test
    @DisplayName("Cuenta las claves distintas registradas")
    void cuentaClavesDistintas() {
        DetectorDeDuplicados detector = new DetectorDeDuplicados();
        detector.esDuplicado("101", 2);
        detector.esDuplicado("102", 3);
        detector.esDuplicado("101", 4);
        assertThat(detector.clavesRegistradas()).isEqualTo(2);
    }
}
