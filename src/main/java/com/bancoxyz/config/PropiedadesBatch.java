package com.bancoxyz.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Parametros de negocio y de rendimiento de la migracion, externalizados en
 * {@code application.properties} bajo el prefijo {@code banco.batch}.
 *
 * <p>Las tasas de interes y los umbrales de anomalia no deben vivir dentro de los
 * {@code ItemProcessor}: el area de riesgo del banco las ajusta sin recompilar.</p>
 */
@Component
@ConfigurationProperties(prefix = "banco.batch")
public class PropiedadesBatch {

    /** Cantidad de items por transaccion (commit interval) en los Steps orientados a chunk. */
    private int tamanoChunk = 100;

    /** Maximo de filas que se pueden omitir antes de dar la migracion por fallida. */
    private int limiteOmisiones = 1000;

    /** Reintentos ante un error transitorio de base de datos antes de propagar el fallo. */
    private int limiteReintentos = 3;

    /** Monto sobre el cual una transaccion se marca para revision del area de riesgo. */
    private BigDecimal umbralMontoAtipico = new BigDecimal("2500");

    /** Tasa mensual de las cuentas de ahorro. */
    private BigDecimal tasaAhorro = new BigDecimal("0.00500");

    /** Tasa mensual de los prestamos. */
    private BigDecimal tasaPrestamo = new BigDecimal("0.01500");

    /** Tasa mensual de los creditos hipotecarios. */
    private BigDecimal tasaHipoteca = new BigDecimal("0.00900");

    /** Puntos de tasa adicionales para cuentas de ahorro de titulares de tercera edad. */
    private BigDecimal bonificacionTerceraEdad = new BigDecimal("0.00100");

    /**
     * Activa una falla transitoria simulada en el primer chunk para evidenciar el
     * funcionamiento del RetryPolicy. Solo se enciende en las corridas de demostracion.
     */
    private boolean simularFalloTransitorio = false;

    public int getTamanoChunk() {
        return tamanoChunk;
    }

    public void setTamanoChunk(int tamanoChunk) {
        this.tamanoChunk = tamanoChunk;
    }

    public int getLimiteOmisiones() {
        return limiteOmisiones;
    }

    public void setLimiteOmisiones(int limiteOmisiones) {
        this.limiteOmisiones = limiteOmisiones;
    }

    public int getLimiteReintentos() {
        return limiteReintentos;
    }

    public void setLimiteReintentos(int limiteReintentos) {
        this.limiteReintentos = limiteReintentos;
    }

    public BigDecimal getUmbralMontoAtipico() {
        return umbralMontoAtipico;
    }

    public void setUmbralMontoAtipico(BigDecimal umbralMontoAtipico) {
        this.umbralMontoAtipico = umbralMontoAtipico;
    }

    public BigDecimal getTasaAhorro() {
        return tasaAhorro;
    }

    public void setTasaAhorro(BigDecimal tasaAhorro) {
        this.tasaAhorro = tasaAhorro;
    }

    public BigDecimal getTasaPrestamo() {
        return tasaPrestamo;
    }

    public void setTasaPrestamo(BigDecimal tasaPrestamo) {
        this.tasaPrestamo = tasaPrestamo;
    }

    public BigDecimal getTasaHipoteca() {
        return tasaHipoteca;
    }

    public void setTasaHipoteca(BigDecimal tasaHipoteca) {
        this.tasaHipoteca = tasaHipoteca;
    }

    public BigDecimal getBonificacionTerceraEdad() {
        return bonificacionTerceraEdad;
    }

    public void setBonificacionTerceraEdad(BigDecimal bonificacionTerceraEdad) {
        this.bonificacionTerceraEdad = bonificacionTerceraEdad;
    }

    public boolean isSimularFalloTransitorio() {
        return simularFalloTransitorio;
    }

    public void setSimularFalloTransitorio(boolean simularFalloTransitorio) {
        this.simularFalloTransitorio = simularFalloTransitorio;
    }
}
