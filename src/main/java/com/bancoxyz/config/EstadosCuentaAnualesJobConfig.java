package com.bancoxyz.config;

import com.bancoxyz.batch.listener.RegistroRechazadoSkipListener;
import com.bancoxyz.batch.listener.ResumenJobListener;
import com.bancoxyz.batch.processor.MovimientoAnualItemProcessor;
import com.bancoxyz.batch.reader.LectoresCsv;
import com.bancoxyz.batch.tasklet.EstadosCuentaAnualesTasklet;
import com.bancoxyz.common.Constantes;
import com.bancoxyz.dto.MovimientoAnualCsv;
import com.bancoxyz.entity.MovimientoAnual;
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
 * Job 3 — Generacion de estados de cuenta anuales.
 *
 * <p>Reemplaza la compilacion manual que el area de auditoria armaba a fin de ano con
 * shell scripts. Migra {@code cuentas_anuales.csv} normalizando el signo de los montos y
 * completando descripciones, y luego compila el estado por cuenta y ano.</p>
 *
 * <pre>
 *   procesarMovimientosAnualesStep  ->  compilarEstadosCuentaStep  ->  exportarRechazadosStep
 *            (chunk)                          (tasklet)                     (tasklet)
 * </pre>
 */
@Configuration
public class EstadosCuentaAnualesJobConfig {

    private final ConstructorDePasos constructor;

    public EstadosCuentaAnualesJobConfig(ConstructorDePasos constructor) {
        this.constructor = constructor;
    }

    @Bean
    public Job estadosCuentaAnualesJob(JobRepository jobRepository,
                                       ResumenJobListener resumenJobListener,
                                       Step procesarMovimientosAnualesStep,
                                       Step compilarEstadosCuentaStep,
                                       Step exportarRechazadosStep) {
        return new JobBuilder(Constantes.JOB_ESTADOS_CUENTA_ANUALES, jobRepository)
                .listener(resumenJobListener)
                .start(procesarMovimientosAnualesStep)
                .next(compilarEstadosCuentaStep)
                .next(exportarRechazadosStep)
                .build();
    }

    @Bean
    public Step procesarMovimientosAnualesStep(
            FlatFileItemReader<MovimientoAnualCsv> lectorMovimientosAnuales,
            MovimientoAnualItemProcessor procesadorMovimientosAnuales,
            JpaItemWriter<MovimientoAnual> escritorMovimientosAnuales,
            RegistroRechazadoSkipListener<MovimientoAnualCsv, MovimientoAnual> oyenteRechazoMovimientos) {
        return constructor.pasoDeMigracion(Constantes.STEP_PROCESAR_MOVIMIENTOS,
                lectorMovimientosAnuales, procesadorMovimientosAnuales,
                escritorMovimientosAnuales, oyenteRechazoMovimientos);
    }

    @Bean
    public Step compilarEstadosCuentaStep(EstadosCuentaAnualesTasklet estadosCuentaAnualesTasklet) {
        return constructor.pasoDeTarea(Constantes.STEP_ESTADOS_CUENTA, estadosCuentaAnualesTasklet);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<MovimientoAnualCsv> lectorMovimientosAnuales(
            @Value("#{jobParameters['" + Constantes.PARAM_ENTRADA + "']}") String carpetaEntrada) {
        return LectoresCsv.deMovimientosAnuales(Path.of(carpetaEntrada, Constantes.ARCHIVO_CUENTAS_ANUALES));
    }

    @Bean
    public JpaItemWriter<MovimientoAnual> escritorMovimientosAnuales(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<MovimientoAnual>()
                .entityManagerFactory(entityManagerFactory)
                .usePersist(true)
                .build();
    }

    @Bean
    @StepScope
    public RegistroRechazadoSkipListener<MovimientoAnualCsv, MovimientoAnual> oyenteRechazoMovimientos(
            RegistroRechazadoService bitacora,
            @Value("#{stepExecution.jobExecutionId}") Long jobExecutionId) {
        return new RegistroRechazadoSkipListener<>(Constantes.JOB_ESTADOS_CUENTA_ANUALES,
                Constantes.ARCHIVO_CUENTAS_ANUALES, bitacora, jobExecutionId);
    }
}
