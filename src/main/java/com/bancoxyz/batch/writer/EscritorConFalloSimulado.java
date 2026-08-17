package com.bancoxyz.batch.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.TransientDataAccessResourceException;

/**
 * Decorador de {@code ItemWriter} que simula una caida transitoria de la base de datos.
 *
 * <p>Existe para poder <b>demostrar</b> el {@code RetryPolicy}: sin un fallo real no hay
 * forma de evidenciar que el chunk se reintenta y termina bien. Cuando la propiedad
 * {@code banco.batch.simular-fallo-transitorio} esta activa, el primer intento de
 * escritura lanza una {@link TransientDataAccessResourceException} (el tipo de error que
 * Spring clasifica como recuperable) y el segundo intento delega normalmente.</p>
 *
 * <p>Viene desactivado por defecto: solo se enciende en la corrida de demostracion.</p>
 *
 * @param <T> tipo de entidad que se persiste
 */
public class EscritorConFalloSimulado<T> implements ItemWriter<T> {

    private static final Logger log = LoggerFactory.getLogger(EscritorConFalloSimulado.class);

    private final ItemWriter<T> delegado;
    private final boolean activo;
    private boolean yaFallo;

    public EscritorConFalloSimulado(ItemWriter<T> delegado, boolean activo) {
        this.delegado = delegado;
        this.activo = activo;
    }

    @Override
    public void write(Chunk<? extends T> chunk) throws Exception {
        if (activo && !yaFallo) {
            yaFallo = true;
            log.warn("Simulando caida transitoria de la base de datos en el primer chunk ({} items). "
                    + "El RetryPolicy debe reintentar y completar la escritura.", chunk.size());
            throw new TransientDataAccessResourceException(
                    "Conexion perdida con la base de datos (fallo transitorio simulado)");
        }
        delegado.write(chunk);
    }
}
