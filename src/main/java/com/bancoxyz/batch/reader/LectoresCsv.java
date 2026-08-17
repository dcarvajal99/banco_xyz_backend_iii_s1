package com.bancoxyz.batch.reader;

import com.bancoxyz.dto.FilaLegacy;
import com.bancoxyz.dto.InteresCsv;
import com.bancoxyz.dto.MovimientoAnualCsv;
import com.bancoxyz.dto.TransaccionCsv;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.LineMapper;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Path;

/**
 * Fabrica de los {@code ItemReader} de los tres archivos legacy.
 *
 * <p>Los tres lectores comparten la misma receta: saltar la cabecera, tokenizar por coma,
 * mapear a un DTO de solo texto y anotar el numero de linea de origen. La tolerancia al
 * dato sucio no vive aqui sino en el {@code ItemProcessor}; el lector solo debe conseguir
 * que la fila entre al pipeline.</p>
 */
public final class LectoresCsv {

    private LectoresCsv() {
        // Clase de utilidad: no se instancia.
    }

    /** Lector de {@code transacciones.csv} (id, fecha, monto, tipo). */
    public static FlatFileItemReader<TransaccionCsv> deTransacciones(Path archivo) {
        return construir("lectorTransacciones", archivo, TransaccionCsv.class,
                new String[]{"id", "fecha", "monto", "tipo"});
    }

    /** Lector de {@code intereses.csv} (cuenta_id, nombre, saldo, edad, tipo). */
    public static FlatFileItemReader<InteresCsv> deIntereses(Path archivo) {
        return construir("lectorIntereses", archivo, InteresCsv.class,
                new String[]{"cuentaId", "nombre", "saldo", "edad", "tipo"});
    }

    /** Lector de {@code cuentas_anuales.csv} (cuenta_id, fecha, transaccion, monto, descripcion). */
    public static FlatFileItemReader<MovimientoAnualCsv> deMovimientosAnuales(Path archivo) {
        return construir("lectorMovimientosAnuales", archivo, MovimientoAnualCsv.class,
                new String[]{"cuentaId", "fecha", "transaccion", "monto", "descripcion"});
    }

    private static <T extends FilaLegacy> FlatFileItemReader<T> construir(String nombre, Path archivo,
                                                                         Class<T> tipo, String[] campos) {
        return new FlatFileItemReaderBuilder<T>()
                .name(nombre)
                // El nombre del lector es la clave con la que Spring Batch guarda la posicion
                // de lectura en el ExecutionContext: es lo que permite reanudar un Job caido.
                .resource(new FileSystemResource(archivo))
                .strict(true)
                .encoding("UTF-8")
                .linesToSkip(1)
                .lineMapper(mapeadorConNumeroDeLinea(tipo, campos))
                .build();
    }

    /**
     * Envuelve el {@code DefaultLineMapper} estandar para dejar registrado en cada DTO la
     * linea del archivo de la que salio. Es la unica forma de que un procesador con estado
     * distinga una fila repetida de una fila reprocesada tras un rollback.
     */
    private static <T extends FilaLegacy> LineMapper<T> mapeadorConNumeroDeLinea(Class<T> tipo, String[] campos) {
        DelimitedLineTokenizer tokenizador = new DelimitedLineTokenizer();
        tokenizador.setDelimiter(",");
        tokenizador.setNames(campos);

        BeanWrapperFieldSetMapper<T> mapeadorDeCampos = new BeanWrapperFieldSetMapper<>();
        mapeadorDeCampos.setTargetType(tipo);

        DefaultLineMapper<T> base = new DefaultLineMapper<>();
        base.setLineTokenizer(tokenizador);
        base.setFieldSetMapper(mapeadorDeCampos);

        return (linea, numeroLinea) -> {
            T fila = base.mapLine(linea, numeroLinea);
            fila.setNumeroLinea(numeroLinea);
            return fila;
        };
    }
}
