package com.bancoxyz.common;

import java.util.List;

/**
 * Valores fijos del dominio batch del Banco XYZ.
 *
 * <p>Centralizar aqui los nombres de jobs, steps, archivos y catalogos de valores validos
 * evita los "magic strings" repartidos por las configuraciones y hace que un cambio de
 * nomenclatura sea un cambio en un solo lugar.</p>
 */
public final class Constantes {

    private Constantes() {
        // Clase de utilidad: no se instancia.
    }

    /* ------------------------------------------------------------------ Jobs */

    public static final String JOB_TRANSACCIONES_DIARIAS = "reporteTransaccionesDiariasJob";
    public static final String JOB_INTERESES_MENSUALES = "calculoInteresesMensualesJob";
    public static final String JOB_ESTADOS_CUENTA_ANUALES = "estadosCuentaAnualesJob";
    public static final String JOB_MIGRACION_COMPLETA = "migracionCompletaJob";

    /* ----------------------------------------------------------------- Steps */

    public static final String STEP_PROCESAR_TRANSACCIONES = "procesarTransaccionesStep";
    public static final String STEP_RESUMEN_DIARIO = "generarResumenDiarioStep";
    public static final String STEP_PROCESAR_INTERESES = "procesarInteresesStep";
    public static final String STEP_RESUMEN_INTERESES = "generarResumenInteresesStep";
    public static final String STEP_PROCESAR_MOVIMIENTOS = "procesarMovimientosAnualesStep";
    public static final String STEP_ESTADOS_CUENTA = "compilarEstadosCuentaStep";

    /* -------------------------------------------------- Parametros de los Jobs */

    /** Carpeta con los CSV legacy a procesar (por ejemplo {@code data/semana_3}). */
    public static final String PARAM_ENTRADA = "carpetaEntrada";
    /** Carpeta donde se dejan los archivos de salida generados. */
    public static final String PARAM_SALIDA = "carpetaSalida";
    /** Marca de tiempo que hace unica cada JobInstance. */
    public static final String PARAM_EJECUCION = "ejecucion";

    /* ------------------------------------------------------- Archivos legacy */

    public static final String ARCHIVO_TRANSACCIONES = "transacciones.csv";
    public static final String ARCHIVO_INTERESES = "intereses.csv";
    public static final String ARCHIVO_CUENTAS_ANUALES = "cuentas_anuales.csv";

    /* ------------------------------------------------------ Archivos de salida */

    public static final String SALIDA_TRANSACCIONES = "reporte_transacciones_diarias.csv";
    public static final String SALIDA_INTERESES = "intereses_mensuales.csv";
    public static final String SALIDA_ESTADOS_CUENTA = "estados_cuenta_anuales.csv";

    /* ---------------------------------------------------- Catalogos de valores */

    /** Tipos validos en {@code transacciones.csv}. Cualquier otro valor es dato sucio. */
    public static final List<String> TIPOS_TRANSACCION = List.of("debito", "credito");

    /** Tipos de cuenta validos en {@code intereses.csv}. El valor {@code -1} es dato sucio. */
    public static final List<String> TIPOS_CUENTA = List.of("ahorro", "prestamo", "hipoteca");

    /** Tipos de movimiento validos en {@code cuentas_anuales.csv}. */
    public static final List<String> TIPOS_MOVIMIENTO = List.of("deposito", "retiro", "compra", "pago");

    /** Movimientos que restan saldo: el monto se normaliza a signo negativo. */
    public static final List<String> MOVIMIENTOS_DE_CARGO = List.of("retiro", "compra", "pago");

    /* ------------------------------------------------- Reglas de negocio */

    /** Edad minima aceptada para un titular de cuenta. */
    public static final int EDAD_MINIMA = 18;
    /** Edad maxima aceptada para un titular de cuenta. */
    public static final int EDAD_MAXIMA = 99;
    /** Edad a partir de la cual una cuenta de ahorro recibe la bonificacion de tercera edad. */
    public static final int EDAD_TERCERA_EDAD = 60;

    /** Texto con el que se completa una descripcion vacia (enriquecimiento de datos). */
    public static final String DESCRIPCION_POR_DEFECTO = "Sin descripcion";

    /* ------------------------------------------------------------- Mensajes */

    public static final String MSG_FECHA_INVALIDA = "Fecha ilegible o inexistente: ";
    public static final String MSG_MONTO_INVALIDO = "Monto vacio o no numerico: ";
    public static final String MSG_TIPO_INVALIDO = "Tipo fuera del catalogo permitido: ";
    public static final String MSG_EDAD_INVALIDA = "Edad fuera del rango permitido: ";
    public static final String MSG_SALDO_INVALIDO = "Saldo vacio o no numerico: ";
    public static final String MSG_CUENTA_INVALIDA = "Identificador de cuenta invalido: ";
    public static final String MSG_DUPLICADO = "Registro duplicado, ya procesado en esta ejecucion: ";
    public static final String MSG_MONTO_NO_POSITIVO = "Monto no positivo";
    public static final String MSG_POSIBLE_DUPLICADO = "Posible duplicado por contenido";
    public static final String MSG_MONTO_ATIPICO = "Monto atipico sobre el umbral de revision";
    public static final String MSG_SALDO_CERO = "Saldo cero: el interes calculado es cero";
    public static final String MSG_TITULAR_REPETIDO = "Titular repetido con los mismos datos en otra cuenta";
    public static final String MSG_CUENTA_REPETIDA = "La cuenta aparece mas de una vez en el archivo con datos distintos";
    public static final String MSG_SIGNO_CORREGIDO = "Signo del monto corregido segun el tipo de movimiento";
    public static final String MSG_TIPO_NORMALIZADO = "Tipo normalizado desde la escritura con tilde del archivo legacy: ";
    public static final String MSG_DESCRIPCION_COMPLETADA = "Descripcion vacia completada por el proceso";
}
