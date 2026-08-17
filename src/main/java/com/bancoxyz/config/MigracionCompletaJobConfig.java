package com.bancoxyz.config;

import com.bancoxyz.batch.listener.ResumenJobListener;
import com.bancoxyz.batch.tasklet.ExportarRechazadosTasklet;
import com.bancoxyz.common.Constantes;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/**
 * Job 4 — Migracion completa, los tres procesos en paralelo.
 *
 * <p>Los tres archivos legacy son independientes entre si, de modo que no hay razon para
 * procesarlos en serie. Este Job usa un {@code split} sobre tres flujos: el tiempo total
 * pasa a ser el del proceso mas lento y no la suma de los tres, que es la ventana
 * nocturna que el banco necesita recuperar.</p>
 *
 * <pre>
 *            +-- flujoTransacciones (procesar -> resumen diario)  --+
 *   split ---+-- flujoIntereses     (procesar -> resumen)          --+--> exportarRechazadosStep
 *            +-- flujoEstadosCuenta (procesar -> estados de cuenta)--+
 * </pre>
 *
 * <p>La exportacion de rechazos queda despues de la union: al compartir todos los flujos
 * la misma JobExecution, un unico archivo consolida lo que quedo fuera en los tres.</p>
 */
@Configuration
public class MigracionCompletaJobConfig {

    private final ConstructorDePasos constructor;

    public MigracionCompletaJobConfig(ConstructorDePasos constructor) {
        this.constructor = constructor;
    }

    /**
     * Step compartido por los cuatro Jobs: vuelca la bitacora de rechazos de la corrida.
     * El nombre del archivo se deriva del Job en ejecucion, asi que no hay colisiones.
     */
    @Bean
    public Step exportarRechazadosStep(ExportarRechazadosTasklet exportarRechazadosTasklet) {
        return constructor.pasoDeTarea("exportarRechazadosStep", exportarRechazadosTasklet);
    }

    @Bean
    public Job migracionCompletaJob(JobRepository jobRepository,
                                    ResumenJobListener resumenJobListener,
                                    Step procesarTransaccionesStep,
                                    Step generarResumenDiarioStep,
                                    Step procesarInteresesStep,
                                    Step generarResumenInteresesStep,
                                    Step procesarMovimientosAnualesStep,
                                    Step compilarEstadosCuentaStep,
                                    Step exportarRechazadosStep) {

        Flow flujoTransacciones = new FlowBuilder<Flow>("flujoTransacciones")
                .start(procesarTransaccionesStep)
                .next(generarResumenDiarioStep)
                .build();

        Flow flujoIntereses = new FlowBuilder<Flow>("flujoIntereses")
                .start(procesarInteresesStep)
                .next(generarResumenInteresesStep)
                .build();

        Flow flujoEstadosCuenta = new FlowBuilder<Flow>("flujoEstadosCuenta")
                .start(procesarMovimientosAnualesStep)
                .next(compilarEstadosCuentaStep)
                .build();

        return new JobBuilder(Constantes.JOB_MIGRACION_COMPLETA, jobRepository)
                .listener(resumenJobListener)
                .start(flujoTransacciones)
                .split(new SimpleAsyncTaskExecutor("migracion-"))
                .add(flujoIntereses, flujoEstadosCuenta)
                .next(exportarRechazadosStep)
                .end()
                .build();
    }
}
