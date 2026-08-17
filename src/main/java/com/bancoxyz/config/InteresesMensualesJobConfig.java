package com.bancoxyz.config;

import com.bancoxyz.batch.listener.RegistroRechazadoSkipListener;
import com.bancoxyz.batch.listener.ResumenJobListener;
import com.bancoxyz.batch.processor.InteresItemProcessor;
import com.bancoxyz.batch.reader.LectoresCsv;
import com.bancoxyz.batch.tasklet.ResumenInteresesTasklet;
import com.bancoxyz.common.Constantes;
import com.bancoxyz.dto.InteresCsv;
import com.bancoxyz.entity.CuentaInteres;
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
 * Job 2 — Calculo de intereses mensuales.
 *
 * <p>Reemplaza el shell script que aplicaba las tasas sobre el maestro de cuentas. Migra
 * {@code intereses.csv}, aplica la tasa que corresponde a cada producto, deja actualizado
 * el saldo final en la base de datos y exporta el detalle liquidado.</p>
 *
 * <pre>
 *   procesarInteresesStep  ->  generarResumenInteresesStep  ->  exportarRechazadosStep
 *        (chunk)                       (tasklet)                     (tasklet)
 * </pre>
 */
@Configuration
public class InteresesMensualesJobConfig {

    private final ConstructorDePasos constructor;

    public InteresesMensualesJobConfig(ConstructorDePasos constructor) {
        this.constructor = constructor;
    }

    @Bean
    public Job calculoInteresesMensualesJob(JobRepository jobRepository,
                                            ResumenJobListener resumenJobListener,
                                            Step procesarInteresesStep,
                                            Step generarResumenInteresesStep,
                                            Step exportarRechazadosStep) {
        return new JobBuilder(Constantes.JOB_INTERESES_MENSUALES, jobRepository)
                .listener(resumenJobListener)
                .start(procesarInteresesStep)
                .next(generarResumenInteresesStep)
                .next(exportarRechazadosStep)
                .build();
    }

    @Bean
    public Step procesarInteresesStep(FlatFileItemReader<InteresCsv> lectorIntereses,
                                      InteresItemProcessor procesadorIntereses,
                                      JpaItemWriter<CuentaInteres> escritorIntereses,
                                      RegistroRechazadoSkipListener<InteresCsv, CuentaInteres> oyenteRechazoIntereses) {
        return constructor.pasoDeMigracion(Constantes.STEP_PROCESAR_INTERESES,
                lectorIntereses, procesadorIntereses, escritorIntereses, oyenteRechazoIntereses);
    }

    @Bean
    public Step generarResumenInteresesStep(ResumenInteresesTasklet resumenInteresesTasklet) {
        return constructor.pasoDeTarea(Constantes.STEP_RESUMEN_INTERESES, resumenInteresesTasklet);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<InteresCsv> lectorIntereses(
            @Value("#{jobParameters['" + Constantes.PARAM_ENTRADA + "']}") String carpetaEntrada) {
        return LectoresCsv.deIntereses(Path.of(carpetaEntrada, Constantes.ARCHIVO_INTERESES));
    }

    @Bean
    public JpaItemWriter<CuentaInteres> escritorIntereses(EntityManagerFactory entityManagerFactory) {
        return new JpaItemWriterBuilder<CuentaInteres>()
                .entityManagerFactory(entityManagerFactory)
                .usePersist(true)
                .build();
    }

    @Bean
    @StepScope
    public RegistroRechazadoSkipListener<InteresCsv, CuentaInteres> oyenteRechazoIntereses(
            RegistroRechazadoService bitacora,
            @Value("#{stepExecution.jobExecutionId}") Long jobExecutionId) {
        return new RegistroRechazadoSkipListener<>(Constantes.JOB_INTERESES_MENSUALES,
                Constantes.ARCHIVO_INTERESES, bitacora, jobExecutionId);
    }
}
