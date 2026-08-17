package com.bancoxyz.batch.listener;

import com.bancoxyz.repository.RegistroRechazadoRepository;
import com.bancoxyz.service.RegistroRechazadoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecution;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Imprime al cierre de cada Job el cuadro de control de la migracion.
 *
 * <p>Los contadores de Spring Batch quedan en las tablas {@code BATCH_STEP_EXECUTION},
 * pero el operador que lanza la migracion de madrugada necesita verlos en la consola sin
 * abrir la base de datos. Este resumen es, ademas, la evidencia de ejecucion que se
 * adjunta en el informe.</p>
 */
@Component
public class ResumenJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ResumenJobListener.class);
    private static final String LINEA = "=".repeat(104);
    private static final String SEPARADOR = "-".repeat(104);

    private final RegistroRechazadoRepository rechazados;

    public ResumenJobListener(RegistroRechazadoRepository rechazados) {
        this.rechazados = rechazados;
    }

    @Override
    public void beforeJob(JobExecution ejecucion) {
        log.info("");
        log.info(LINEA);
        log.info("  BANCO XYZ | MIGRACION BATCH -> {}", ejecucion.getJobInstance().getJobName());
        log.info("  Parametros: {}", parametrosCompactos(ejecucion));
        log.info(LINEA);
    }

    @Override
    public void afterJob(JobExecution ejecucion) {
        long omitidos = rechazados.countByJobExecutionIdAndClasificacion(
                ejecucion.getId(), RegistroRechazadoService.CLASIFICACION_OMITIDO);
        long filtrados = rechazados.countByJobExecutionIdAndClasificacion(
                ejecucion.getId(), RegistroRechazadoService.CLASIFICACION_FILTRADO);

        log.info("");
        log.info(LINEA);
        log.info("  RESUMEN DE EJECUCION -> {}", ejecucion.getJobInstance().getJobName());
        log.info("  jobExecutionId={}  estado={}  duracion={}",
                ejecucion.getId(), ejecucion.getStatus(), duracion(ejecucion.getStartTime(), ejecucion.getEndTime()));
        log.info(SEPARADOR);
        log.info(String.format("  %-34s %8s %9s %9s %10s %10s  %s",
                "STEP", "LEIDOS", "ESCRITOS", "OMITIDOS", "FILTRADOS", "ROLLBACKS", "ESTADO"));
        for (StepExecution step : ejecucion.getStepExecutions()) {
            log.info(String.format("  %-34s %8d %9d %9d %10d %10d  %s",
                    step.getStepName(),
                    step.getReadCount(),
                    step.getWriteCount(),
                    step.getSkipCount(),
                    step.getFilterCount(),
                    // Un rollback es un chunk revertido: dato sucio omitido o reintento.
                    step.getRollbackCount(),
                    step.getStatus()));
        }
        log.info(SEPARADOR);
        log.info("  Bitacora de rechazos: {} registros (omitidos por excepcion={}, filtrados por duplicado={})",
                omitidos + filtrados, omitidos, filtrados);
        if (!ejecucion.getAllFailureExceptions().isEmpty()) {
            log.error("  Fallas registradas: {}", ejecucion.getAllFailureExceptions());
        }
        log.info(LINEA);
        log.info("");
    }

    /** Los JobParameters en su toString() ocupan tres lineas: aqui se resumen a clave=valor. */
    private static String parametrosCompactos(JobExecution ejecucion) {
        return ejecucion.getJobParameters().getParameters().entrySet().stream()
                .map(entrada -> entrada.getKey() + "=" + entrada.getValue().getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String duracion(LocalDateTime inicio, LocalDateTime fin) {
        if (inicio == null || fin == null) {
            return "n/d";
        }
        return Duration.between(inicio, fin).toMillis() + " ms";
    }
}
