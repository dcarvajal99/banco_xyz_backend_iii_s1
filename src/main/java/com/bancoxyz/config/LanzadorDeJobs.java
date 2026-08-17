package com.bancoxyz.config;

import com.bancoxyz.common.Constantes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Punto de entrada por linea de comandos de la migracion.
 *
 * <p>Cumple el papel que en el sistema legacy tenia el shell script del operador: recibe
 * que proceso correr y sobre que carpeta, y se lo entrega al {@link JobLauncher}, que es
 * quien crea el contexto de ejecucion del Job.</p>
 *
 * <p>Se lanza explicitamente en vez de dejar que Spring Boot ejecute todos los Jobs al
 * arrancar ({@code spring.batch.job.enabled=false}), porque el banco necesita poder correr
 * el cierre de intereses sin volver a procesar las transacciones del dia.</p>
 *
 * <h2>Uso</h2>
 * <pre>
 *   java -jar banco-xyz-batch.jar --job=transacciones --dataset=semana_3
 *   java -jar banco-xyz-batch.jar --job=intereses    --entrada=data/semana_1
 *   java -jar banco-xyz-batch.jar --job=completa     --salida=salida
 * </pre>
 */
@Component
// Se apaga en las pruebas (banco.batch.lanzador=false): alli los Jobs los dispara el test,
// no el arranque de la aplicacion.
@ConditionalOnProperty(name = "banco.batch.lanzador", havingValue = "true", matchIfMissing = true)
public class LanzadorDeJobs implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(LanzadorDeJobs.class);

    /** Alias corto -> nombre real del Job, para no escribir el nombre completo en la consola. */
    private static final Map<String, String> ALIAS = new LinkedHashMap<>();

    static {
        ALIAS.put("transacciones", Constantes.JOB_TRANSACCIONES_DIARIAS);
        ALIAS.put("intereses", Constantes.JOB_INTERESES_MENSUALES);
        ALIAS.put("estados", Constantes.JOB_ESTADOS_CUENTA_ANUALES);
        ALIAS.put("completa", Constantes.JOB_MIGRACION_COMPLETA);
    }

    /** Carpeta del repositorio legacy que se procesa si no se indica otra. */
    private static final String DATASET_POR_DEFECTO = "semana_1";
    private static final String ENTRADA_POR_DEFECTO = "data";
    private static final String SALIDA_POR_DEFECTO = "salida";

    private final JobLauncher jobLauncher;
    private final Map<String, Job> jobs;
    private int codigoSalida;

    public LanzadorDeJobs(JobLauncher jobLauncher, Map<String, Job> jobs) {
        this.jobLauncher = jobLauncher;
        this.jobs = jobs;
    }

    @Override
    public void run(ApplicationArguments argumentos) throws Exception {
        String alias = primerValor(argumentos, "job", "completa");
        String nombreJob = ALIAS.get(alias.toLowerCase());
        if (nombreJob == null) {
            log.error("Job desconocido: '{}'. Opciones validas: {}", alias, ALIAS.keySet());
            codigoSalida = 2;
            return;
        }

        Job job = jobs.get(nombreJob);
        if (job == null) {
            log.error("El Job '{}' no esta registrado en el contexto", nombreJob);
            codigoSalida = 2;
            return;
        }

        Path carpetaEntrada = resolverEntrada(argumentos);
        if (!Files.isDirectory(carpetaEntrada)) {
            log.error("La carpeta de entrada no existe: {}", carpetaEntrada.toAbsolutePath());
            codigoSalida = 2;
            return;
        }
        Path carpetaSalida = Path.of(primerValor(argumentos, "salida", SALIDA_POR_DEFECTO));
        Files.createDirectories(carpetaSalida);

        JobParameters parametros = new JobParametersBuilder()
                .addString(Constantes.PARAM_ENTRADA, carpetaEntrada.toString())
                .addString(Constantes.PARAM_SALIDA, carpetaSalida.toString())
                // Marca de tiempo: hace unica la JobInstance para poder relanzar el mismo
                // archivo sin chocar con JobInstanceAlreadyCompleteException.
                .addString(Constantes.PARAM_EJECUCION, LocalDateTime.now().toString())
                .toJobParameters();

        JobExecution ejecucion = jobLauncher.run(job, parametros);
        if (!ejecucion.getStatus().isUnsuccessful()) {
            log.info("Job '{}' finalizado con estado {}", nombreJob, ejecucion.getStatus());
        } else {
            log.error("Job '{}' finalizado con estado {}", nombreJob, ejecucion.getStatus());
            codigoSalida = 1;
        }
    }

    private Path resolverEntrada(ApplicationArguments argumentos) {
        List<String> entrada = argumentos.getOptionValues("entrada");
        if (entrada != null && !entrada.isEmpty()) {
            return Path.of(entrada.get(0));
        }
        return Path.of(ENTRADA_POR_DEFECTO, primerValor(argumentos, "dataset", DATASET_POR_DEFECTO));
    }

    private static String primerValor(ApplicationArguments argumentos, String opcion, String porDefecto) {
        List<String> valores = argumentos.getOptionValues(opcion);
        return valores == null || valores.isEmpty() ? porDefecto : valores.get(0);
    }

    @Override
    public int getExitCode() {
        return codigoSalida;
    }
}
