package com.bancoxyz.config;

import com.bancoxyz.batch.policy.PoliticaOmisionBancaria;
import com.bancoxyz.batch.writer.EscritorConFalloSimulado;
import org.springframework.batch.core.SkipListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Arma los Steps de la migracion con la misma politica de tolerancia a fallos.
 *
 * <p>Los tres procesos del Banco XYZ leen archivos distintos pero necesitan exactamente el
 * mismo comportamiento frente al error: omitir la fila sucia, reintentar el error
 * transitorio y dejar rastro de todo. Concentrarlo aqui evita que cada Job termine con su
 * propia variante de la politica, que es como el sistema legacy llego a tener tres
 * criterios distintos para el mismo problema.</p>
 */
@Component
public class ConstructorDePasos {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final PropiedadesBatch propiedades;

    public ConstructorDePasos(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              PropiedadesBatch propiedades) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.propiedades = propiedades;
    }

    /**
     * Step orientado a chunk: lee, transforma y escribe en bloques del tamano configurado.
     * Cada chunk es una transaccion: si algo falla, se revierte solo ese bloque.
     *
     * @param nombre nombre del Step (queda en los metadatos de Spring Batch)
     * @param lector origen de datos legacy
     * @param procesador validaciones y transformaciones
     * @param escritor destino relacional
     * @param oyenteOmision listener que registra en bitacora cada fila descartada
     */
    public <I, O> Step pasoDeMigracion(String nombre,
                                       ItemReader<I> lector,
                                       ItemProcessor<I, O> procesador,
                                       ItemWriter<O> escritor,
                                       SkipListener<I, O> oyenteOmision) {
        return new StepBuilder(nombre, jobRepository)
                .<I, O>chunk(propiedades.getTamanoChunk(), transactionManager)
                .reader(lector)
                .processor(procesador)
                .writer(new EscritorConFalloSimulado<>(escritor, propiedades.isSimularFalloTransitorio()))
                .faultTolerant()
                // Omision: solo el dato sucio, y con tope. Ver PoliticaOmisionBancaria.
                .skipPolicy(new PoliticaOmisionBancaria(propiedades.getLimiteOmisiones()))
                // Reintento: los errores transitorios de base de datos no deben botar la migracion.
                .retryLimit(propiedades.getLimiteReintentos())
                .retry(TransientDataAccessException.class)
                .listener(oyenteOmision)
                .build();
    }

    /** Step de tarea unica: agregaciones y generacion de archivos de salida. */
    public Step pasoDeTarea(String nombre, Tasklet tarea) {
        return new StepBuilder(nombre, jobRepository)
                .tasklet(tarea, transactionManager)
                .build();
    }
}
