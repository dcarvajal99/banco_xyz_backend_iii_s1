package com.bancoxyz.config;

import com.bancoxyz.batch.listener.RegistroRechazadoSkipListener;
import com.bancoxyz.batch.listener.ResumenJobListener;
import com.bancoxyz.batch.processor.TransaccionItemProcessor;
import com.bancoxyz.batch.reader.LectoresCsv;
import com.bancoxyz.batch.tasklet.ResumenDiarioTasklet;
import com.bancoxyz.common.Constantes;
import com.bancoxyz.dto.TransaccionCsv;
import com.bancoxyz.entity.Transaccion;
import com.bancoxyz.service.RegistroRechazadoService;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * Job 1 — Reporte de transacciones diarias.
 *
 * <p>Reemplaza el proceso COBOL que cada noche listaba los movimientos del dia. El flujo
 * es: migrar {@code transacciones.csv} detectando anomalias, compilar el resumen por dia
 * y exportar la bitacora de lo que quedo fuera.</p>
 *
 * <pre>
 *   procesarTransaccionesStep  ->  generarResumenDiarioStep  ->  exportarRechazadosStep
 *        (chunk)                        (tasklet)                     (tasklet)
 * </pre>
 */
@Configuration
public class TransaccionesDiariasJobConfig {

    private final ConstructorDePasos constructor;

    public TransaccionesDiariasJobConfig(ConstructorDePasos constructor) {
        this.constructor = constructor;
    }

    /**
     * El Job es la unidad que el JobLauncher ejecuta y que el JobRepository registra:
     * cada corrida queda como una JobExecution con sus parametros y su estado.
     */
    @Bean
    public Job reporteTransaccionesDiariasJob(JobRepository jobRepository,
                                              ResumenJobListener resumenJobListener,
                                              Step procesarTransaccionesStep,
                                              Step generarResumenDiarioStep,
                                              Step exportarRechazadosStep) {
        return new JobBuilder(Constantes.JOB_TRANSACCIONES_DIARIAS, jobRepository)
                .listener(resumenJobListener)
                .start(procesarTransaccionesStep)
                .next(generarResumenDiarioStep)
                .next(exportarRechazadosStep)
                .build();
    }

    @Bean
    public Step procesarTransaccionesStep(FlatFileItemReader<TransaccionCsv> lectorTransacciones,
                                          TransaccionItemProcessor procesadorTransacciones,
                                          JpaItemWriter<Transaccion> escritorTransacciones,
                                          RegistroRechazadoSkipListener<TransaccionCsv, Transaccion> oyenteRechazoTransacciones) {
        return constructor.pasoDeMigracion(Constantes.STEP_PROCESAR_TRANSACCIONES,
                lectorTransacciones, procesadorTransacciones, escritorTransacciones, oyenteRechazoTransacciones);
    }

    @Bean
    public Step generarResumenDiarioStep(ResumenDiarioTasklet resumenDiarioTasklet) {
        return constructor.pasoDeTarea(Constantes.STEP_RESUMEN_DIARIO, resumenDiarioTasklet);
    }

    /**
     * El lector es {@code @StepScope} porque la carpeta de entrada llega como parametro
     * del Job: hasta que no hay una ejecucion concreta, la ruta no existe.
     */
    @Bean
    @StepScope
    public FlatFileItemReader<TransaccionCsv> lectorTransacciones(
            @Value("#{jobParameters['" + Constantes.PARAM_ENTRADA + "']}") String carpetaEntrada) {
        return LectoresCsv.deTransacciones(Path.of(carpetaEntrada, Constantes.ARCHIVO_TRANSACCIONES));
    }

    @Bean
    public JpaItemWriter<Transaccion> escritorTransacciones(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<Transaccion>()
                .entityManagerFactory(entityManagerFactory)
                // persist() en vez de merge(): las filas migradas siempre son nuevas.
                .usePersist(true)
                .build();
    }

    @Bean
    @StepScope
    public RegistroRechazadoSkipListener<TransaccionCsv, Transaccion> oyenteRechazoTransacciones(
            RegistroRechazadoService bitacora,
            @Value("#{stepExecution.jobExecutionId}") Long jobExecutionId) {
        return new RegistroRechazadoSkipListener<>(Constantes.JOB_TRANSACCIONES_DIARIAS,
                Constantes.ARCHIVO_TRANSACCIONES, bitacora, jobExecutionId);
    }
}
