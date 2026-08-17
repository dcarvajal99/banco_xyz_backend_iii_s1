-- ============================================================================
-- Consultas de evidencia de la migracion batch del Banco XYZ
-- Uso: docker exec -i banco-xyz-db psql -U banco -d banco_xyz -f - < consultas_evidencia.sql
-- ============================================================================

-- 1. Historial de ejecuciones registrado por Spring Batch (JobRepository)
SELECT e.job_execution_id AS ejecucion,
       i.job_name         AS job,
       e.status           AS estado,
       e.exit_code        AS salida,
       to_char(e.start_time, 'HH24:MI:SS')                              AS inicio,
       round(extract(epoch FROM (e.end_time - e.start_time)) * 1000)    AS ms
FROM   batch_job_execution e
JOIN   batch_job_instance i ON i.job_instance_id = e.job_instance_id
ORDER  BY e.job_execution_id;

-- 2. Contadores por Step (lo mismo que imprime el resumen de consola)
SELECT s.step_execution_id AS id,
       s.step_name         AS step,
       s.read_count        AS leidos,
       s.write_count       AS escritos,
       s.process_skip_count + s.read_skip_count + s.write_skip_count AS omitidos,
       s.filter_count      AS filtrados,
       s.rollback_count    AS rollbacks,
       s.status            AS estado
FROM   batch_step_execution s
ORDER  BY s.step_execution_id;

-- 3. Volumen migrado por tabla destino
SELECT 'transaccion' AS tabla, count(*) AS filas FROM transaccion
UNION ALL SELECT 'resumen_diario',       count(*) FROM resumen_diario
UNION ALL SELECT 'cuenta_interes',       count(*) FROM cuenta_interes
UNION ALL SELECT 'movimiento_anual',     count(*) FROM movimiento_anual
UNION ALL SELECT 'estado_cuenta_anual',  count(*) FROM estado_cuenta_anual
UNION ALL SELECT 'registro_rechazado',   count(*) FROM registro_rechazado
ORDER  BY 1;

-- 4. Reporte de transacciones diarias del Job 1 (ejecucion 1, data/semana_1)
SELECT fecha, cantidad_transacciones, total_debitos, total_creditos,
       (total_creditos - total_debitos) AS saldo_neto, monto_maximo, cantidad_anomalias
FROM   resumen_diario
WHERE  job_execution_id = 1
ORDER  BY fecha;

-- 5. Intereses mensuales calculados por el Job 2 (ejecucion 2, data/semana_1)
SELECT cuenta_id, nombre, tipo, edad, saldo_inicial, tasa_mensual,
       interes, saldo_final, anomalia
FROM   cuenta_interes
WHERE  job_execution_id = 2
ORDER  BY cuenta_id;

-- 6. Estados de cuenta anuales del Job 3 (ejecucion 3, data/semana_1)
SELECT cuenta_id, anio, cantidad_movimientos, total_depositos, total_cargos,
       saldo_neto, primera_fecha, ultima_fecha, movimientos_con_anomalia
FROM   estado_cuenta_anual
WHERE  job_execution_id = 3
ORDER  BY cuenta_id;

-- 7. Bitacora de rechazos: que quedo fuera, de que linea y por que (data/semana_2)
SELECT archivo, numero_linea AS linea, clasificacion, motivo, contenido
FROM   registro_rechazado
WHERE  job_execution_id = 4
ORDER  BY archivo, numero_linea;

-- 8. Que corrigio y que marco el Job 1 sobre el volumen grande (data/semana_3)
SELECT count(*)                                                        AS migradas,
       count(*) FILTER (WHERE anomalia)                                AS con_anomalia,
       count(*) FILTER (WHERE observacion LIKE '%Fecha normalizada%')  AS fecha_corregida,
       count(*) FILTER (WHERE observacion LIKE '%Monto no positivo%')  AS monto_no_positivo,
       count(*) FILTER (WHERE observacion LIKE '%Monto atipico%')      AS monto_atipico,
       count(*) FILTER (WHERE observacion LIKE '%Posible duplicado%')  AS posible_duplicado
FROM   transaccion
WHERE  job_execution_id = 5;

-- 9. Motivos de rechazo agrupados sobre el volumen grande (data/semana_3)
SELECT archivo,
       split_part(motivo, ':', 1) AS motivo,
       count(*)                   AS casos
FROM   registro_rechazado
WHERE  job_execution_id = 5
GROUP  BY archivo, split_part(motivo, ':', 1)
ORDER  BY archivo, casos DESC;
