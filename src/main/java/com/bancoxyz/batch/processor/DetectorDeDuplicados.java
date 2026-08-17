package com.bancoxyz.batch.processor;

import java.util.HashMap;
import java.util.Map;

/**
 * Detecta claves repetidas dentro de una misma ejecucion de un Step, sin confundirse
 * cuando Spring Batch reprocesa una fila.
 *
 * <p>Guardar solo un {@code Set} de claves vistas parece suficiente hasta que aparece el
 * primer dato sucio: al fallar un item, el chunk completo se revierte y se vuelve a
 * procesar item por item, de modo que las filas buenas de ese chunk pasan por el
 * procesador <b>dos veces</b> y el {@code Set} las marcaria como duplicadas. Recordando la
 * linea de la primera aparicion, una fila que vuelve a pasar por su propia linea se
 * reconoce como reproceso y no como duplicado.</p>
 */
public class DetectorDeDuplicados {

    private final Map<String, Integer> primeraAparicion = new HashMap<>();

    /**
     * @param clave clave de negocio del registro (id, cuenta, contenido completo, etc.)
     * @param numeroLinea linea del archivo de la que salio la fila
     * @return {@code true} si la clave ya aparecio en <em>otra</em> linea del archivo
     */
    public boolean esDuplicado(String clave, int numeroLinea) {
        Integer primera = primeraAparicion.putIfAbsent(clave, numeroLinea);
        return primera != null && primera != numeroLinea;
    }

    /** Cantidad de claves distintas registradas hasta el momento. */
    public int clavesRegistradas() {
        return primeraAparicion.size();
    }
}
