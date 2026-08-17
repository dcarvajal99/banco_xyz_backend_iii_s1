# Banco XYZ — Migración de procesos batch legacy con Spring Batch

Migración de los tres procesos por lotes del sistema legacy del **Banco XYZ** (COBOL y shell
scripts) a una arquitectura moderna basada en **Spring Batch 5**, **Spring Boot 3.5** y
**PostgreSQL**.

> Actividad formativa — *Analizando la arquitectura batch para procesar datos*
> Experiencia 1, Semana 1 · **Desarrollo Backend III (PBY2203)** · Duoc UC
> Datos de origen: <https://github.com/KariVillagran/bank_legacy_data>

---

## 1. Objetivo del proyecto

El Banco XYZ mantiene tres procesos nocturnos escritos en COBOL y shell que ya no puede
evolucionar: no son reiniciables, no dejan trazas de lo que descartan y cada error de dato
obliga a reprocesar el archivo completo a la mañana siguiente.

Este proyecto los reescribe en Spring Batch conservando la lógica de negocio y agregando lo
que el sistema legacy nunca tuvo: **transaccionalidad por bloques, política explícita de
omisión y reintento, bitácora auditable de todo lo descartado y metadatos de ejecución
persistidos**.

| # | Proceso legacy | Job en Spring Batch | Qué produce |
|---|---|---|---|
| 1 | Reporte de transacciones diarias | `reporteTransaccionesDiariasJob` | Transacciones normalizadas + resumen por día con anomalías detectadas |
| 2 | Cálculo de intereses mensuales | `calculoInteresesMensualesJob` | Interés y saldo final por cuenta, actualizados en base de datos |
| 3 | Estados de cuenta anuales | `estadosCuentaAnualesJob` | Movimientos normalizados + estado de cuenta por cuenta y año para auditoría |
| 4 | *(nuevo)* Cierre nocturno completo | `migracionCompletaJob` | Los tres procesos **en paralelo** y una bitácora de rechazos consolidada |

---

## 2. Arquitectura Spring Batch

```
                         ┌──────────────────┐
   línea de comandos ───►│   JobLauncher    │──► crea la JobExecution
   (LanzadorDeJobs)      └────────┬─────────┘
                                  │
                         ┌────────▼─────────┐        ┌──────────────────┐
                         │       Job        │◄──────►│  JobRepository   │
                         └────────┬─────────┘        │  (tablas BATCH_*)│
                                  │                  └──────────────────┘
              ┌───────────────────┼───────────────────┐
              ▼                   ▼                   ▼
      ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
      │ Step (chunk)  │   │ Step (tasklet)│   │ Step (tasklet)│
      │ leer→transf.→ │   │  agregación   │   │  bitácora de  │
      │    escribir   │   │  + archivo    │   │   rechazos    │
      └───────┬───────┘   └───────────────┘   └───────────────┘
              │
   ┌──────────┼───────────┬──────────────┐
   ▼          ▼           ▼              ▼
ItemReader  ItemProcessor  ItemWriter   SkipListener
(CSV legacy) (validar y    (JPA →       (registra lo
             corregir)     PostgreSQL)   descartado)
```

Cada Job de negocio se descompone en **tres Steps**, siguiendo el principio de dividir un
proceso grande en pasos pequeños y reiniciables:

1. **Step orientado a chunk** — `ItemReader` → `ItemProcessor` → `ItemWriter`, con commit
   cada 100 ítems y tolerancia a fallos.
2. **Step de agregación (`Tasklet`)** — compila el reporte sobre lo ya persistido y genera
   el archivo de salida.
3. **Step de bitácora (`Tasklet`)** — exporta a CSV lo que quedó fuera de la migración.

### Estructura del código (`com.bancoxyz`, 10 paquetes)

```
batch/
  listener/   ResumenJobListener (cuadro de control por consola)
              RegistroRechazadoSkipListener (cada omisión → bitácora auditable)
  policy/     PoliticaOmisionBancaria (qué se omite y qué detiene la migración)
  processor/  TransaccionItemProcessor · InteresItemProcessor · MovimientoAnualItemProcessor
              DetectorDeDuplicados (deduplicación segura ante reprocesos)
  reader/     LectoresCsv (FlatFileItemReader + número de línea de origen)
  tasklet/    ResumenDiarioTasklet · ResumenInteresesTasklet
              EstadosCuentaAnualesTasklet · ExportarRechazadosTasklet
  writer/     EscritorConFalloSimulado (para evidenciar el RetryPolicy)
common/       Constantes (nombres de jobs/steps, catálogos y mensajes)
config/       PropiedadesBatch · ConstructorDePasos · LanzadorDeJobs
              …JobConfig (uno por Job) · MigracionCompletaJobConfig
dto/          FilaLegacy + TransaccionCsv · InteresCsv · MovimientoAnualCsv
entity/       Transaccion · ResumenDiario · CuentaInteres · MovimientoAnual
              EstadoCuentaAnual · RegistroRechazado
exception/    DatoInvalidoException
repository/   6 JpaRepository
service/      RegistroRechazadoService (bitácora en transacción propia)
util/         ParseadorFechas · ParseadorNumeros · EscritorCsv
```

---

## 3. Stack técnico

| Componente | Versión | Rol |
|---|---|---|
| Java | 17 | Lenguaje (compilado con JDK 21) |
| Spring Boot | 3.5.16 | Autoconfiguración y empaquetado |
| Spring Batch | 5.2.x | Jobs, Steps, JobRepository, tolerancia a fallos |
| Spring Data JPA / Hibernate | 6.x | Persistencia de los datos migrados |
| PostgreSQL | 17 (Docker) | Base de datos destino **y** JobRepository |
| H2 | 2.x | Base en memoria para las pruebas |
| JUnit 5 · Mockito · AssertJ | — | 99 pruebas |
| JaCoCo | 0.8.12 | Cobertura (96,4 % instrucciones) |

---

## 4. Cómo ejecutar el proyecto

### 4.1 Requisitos

- JDK 17 o superior (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` en macOS)
- Docker (para PostgreSQL)

### 4.2 Levantar la base de datos

```bash
docker compose up -d
docker compose ps          # esperar a que quede "healthy"
```

Expone PostgreSQL en `localhost:5433` (base `banco_xyz`, usuario `banco`, clave `banco123`).
El puerto 5433 evita chocar con una instancia local en el 5432. Las tablas de negocio y las
tablas `BATCH_*` del JobRepository se crean solas en el primer arranque.

### 4.3 Compilar y probar

```bash
./mvnw clean package          # compila, corre las 99 pruebas y arma el jar
```

Cobertura JaCoCo en `target/site/jacoco/index.html`.

### 4.4 Ejecutar los Jobs

```bash
# Un proceso a la vez
java -jar target/banco-xyz-batch-1.0.0.jar --job=transacciones --dataset=semana_1
java -jar target/banco-xyz-batch-1.0.0.jar --job=intereses     --dataset=semana_1
java -jar target/banco-xyz-batch-1.0.0.jar --job=estados       --dataset=semana_1

# Los tres en paralelo (cierre nocturno completo)
java -jar target/banco-xyz-batch-1.0.0.jar --job=completa --dataset=semana_3
```

| Argumento | Valores | Por defecto |
|---|---|---|
| `--job` | `transacciones`, `intereses`, `estados`, `completa` | `completa` |
| `--dataset` | `semana_1`, `semana_2`, `semana_3` (carpetas de `data/`) | `semana_1` |
| `--entrada` | ruta completa a una carpeta con los tres CSV | — |
| `--salida` | carpeta donde dejar los archivos generados | `salida` |

El proceso devuelve **código de salida 0** si el Job terminó `COMPLETED`, **1** si falló y
**2** si los argumentos son inválidos, de modo que un planificador (cron, Control-M) puede
encadenar o alertar.

---

## 5. Los datos legacy y sus defectos

El repositorio `bank_legacy_data` entrega tres CSV con defectos deliberados. `data/semana_1`
es el juego base de esta actividad; `semana_2` agrega formatos inconsistentes y `semana_3`
son 1.000 filas por archivo con todos los defectos mezclados.

| Archivo | Columnas | Defectos presentes |
|---|---|---|
| `transacciones.csv` | id, fecha, monto, tipo | montos negativos o cero, montos vacíos, fechas en 4 formatos, fechas inexistentes (`2024-13-01`), tipo `invalid`, transacciones repetidas |
| `intereses.csv` | cuenta_id, nombre, saldo, edad, tipo | saldos vacíos, edades vacías o fuera de rango (100), tipo `-1`, titular `Unknown`, cuentas repetidas |
| `cuentas_anuales.csv` | cuenta_id, fecha, transaccion, monto, descripción | fechas en formatos mixtos, montos vacíos, depósitos con monto negativo, descripciones faltantes, el mismo tipo escrito con y sin tilde (`deposito` / `depósito`) |

### Criterio de tratamiento

La decisión de diseño central es que **no todo dato defectuoso merece el mismo trato**:

| Situación | Tratamiento | Por qué |
|---|---|---|
| Fecha ilegible, monto no numérico, tipo fuera del catálogo, edad fuera de rango | **Omitir** (`DatoInvalidoException` → `SkipPolicy` → bitácora) | No hay forma de deducir el valor correcto sin inventarlo |
| Fecha en formato legacy, tipo escrito con tilde, descripción vacía, signo del monto inconsistente, titular `Unknown` | **Corregir** y dejar anotado en `observacion` | El dato correcto es deducible sin ambigüedad |
| Monto no positivo, monto sobre el umbral, cuenta o movimiento repetido | **Migrar marcado** como `anomalia = true` | Un banco no puede hacer desaparecer un movimiento: se señala para revisión |
| Fila idéntica repetida (todos los campos) | **Filtrar** (`ItemProcessor` devuelve `null`) → bitácora | Es una copia, no aporta información |
| Fallo de base de datos, disco o configuración | **Reintentar** hasta 3 veces; si persiste, **detener el Job** | Seguir adelante produciría una migración incompleta que parece exitosa |

---

## 6. Reglas de negocio implementadas

### Job 1 — Reporte de transacciones diarias

- Normaliza fecha (`uuuu-MM-dd`, `uuuu/MM/dd`, `dd-MM-uuuu`, `dd/MM/uuuu`, resolución
  estricta: `2024-13-01` se rechaza en vez de "corregirse" sola a diciembre).
- Valida `tipo ∈ {debito, credito}`.
- Marca como anomalía: monto ≤ 0, monto > umbral (2.500, configurable) y misma
  fecha/monto/tipo con otro id.
- El resumen diario acumula débitos, créditos, saldo neto, monto máximo y anomalías; los
  montos no positivos cuentan como anomalía pero **no suman a los totales**.

### Job 2 — Cálculo de intereses mensuales

| Tipo de cuenta | Tasa mensual | Semántica |
|---|---|---|
| `ahorro` | 0,500 % | Saldo a favor del cliente; el interés se **abona** |
| `prestamo` | 1,500 % | Saldo = deuda vigente; el interés se **carga** |
| `hipoteca` | 0,900 % | Saldo = deuda vigente; el interés se **carga** |

- **Bonificación de tercera edad**: +0,100 % adicional en cuentas de ahorro de titulares de
  60 años o más.
- Aritmética con `BigDecimal` y redondeo `HALF_UP` a dos decimales (criterio bancario); usar
  `double` acumularía diferencias de centavos sobre miles de filas.
- Se persisten por separado saldo inicial, tasa aplicada, interés y saldo final para que
  auditoría pueda rehacer el cálculo sin volver a correr el batch.
- Edad válida: 18 a 99 años.

### Job 3 — Estados de cuenta anuales

- **Normalización del signo**: el monto se guarda positivo para `deposito` y negativo para
  `retiro`, `compra` y `pago`, derivado del tipo y no del dato de origen. Así el estado de
  cuenta es una suma simple. Un depósito cargado en negativo se corrige *y* se marca como
  anomalía, porque es un error de captura, no una convención distinta.
- **Normalización del tipo**: el archivo escribe el mismo movimiento como `deposito` y como
  `depósito` según quién cargó la fila. `ParseadorNumeros.normalizarTexto` quita los
  diacríticos antes de contrastar contra el catálogo, de modo que ambas formas se migran como
  el mismo tipo y la corrección queda anotada. Sin esto, 52 depósitos de `semana_3` (94.800 en
  montos) desaparecían del estado de cuenta anual.
- Descripción vacía → se completa con `Sin descripcion` (enriquecimiento).
- El estado anual agrupa por cuenta y año: cantidad de movimientos, total de depósitos,
  total de cargos, saldo neto, primera y última fecha, y movimientos con anomalía.

---

## 7. Manejo de errores y excepciones

| Mecanismo | Implementación | Efecto |
|---|---|---|
| `SkipPolicy` | `PoliticaOmisionBancaria` | Omite solo `DatoInvalidoException` y `FlatFileParseException`, con tope de 1.000 omisiones |
| `RetryPolicy` | `.retryLimit(3).retry(TransientDataAccessException.class)` | Reintenta el chunk ante caídas transitorias de base de datos |
| `SkipListener` | `RegistroRechazadoSkipListener` | Cada omisión queda en `registro_rechazado` con archivo, **línea**, motivo y contenido crudo |
| Transacción propia | `RegistroRechazadoService` con `REQUIRES_NEW` | La bitácora sobrevive al rollback del chunk que la originó |
| Chunk transaccional | `chunk(100, transactionManager)` | Un error revierte solo su bloque, no la migración completa |
| Reinicio | `JobRepository` en PostgreSQL + lectores con nombre | Deja preparada la reanudación: el estado de cada Step queda persistido. Para usarla hay que relanzar con los **mismos** `JobParameters`; el lanzador agrega hoy una marca de tiempo identificatoria, que crea una `JobInstance` nueva en cada corrida |

### Deduplicación segura ante reprocesos

Un detalle no evidente de Spring Batch: cuando un ítem falla, **el chunk completo se
revierte y se vuelve a procesar ítem por ítem**. Un `ItemProcessor` con estado que solo
guardara un `Set` de claves vistas marcaría como duplicadas las filas buenas de ese chunk al
verlas por segunda vez.

La solución fue anotar en cada DTO el **número de línea del archivo de origen**
(`FilaLegacy` + un `LineMapper` propio) y que `DetectorDeDuplicados` recuerde la línea de la
primera aparición: una fila que vuelve a pasar por *su propia* línea es un reproceso, no un
duplicado. El número de línea sirve además para decirle al área de datos exactamente qué
corregir.

### Demostración del reintento

```bash
java -jar target/banco-xyz-batch-1.0.0.jar --job=transacciones --dataset=semana_1 \
     --banco.batch.simular-fallo-transitorio=true
```

`EscritorConFalloSimulado` lanza una `TransientDataAccessResourceException` en el primer
chunk. El resumen muestra `ROLLBACKS = 1` y aun así `ESCRITOS = 10` y estado `COMPLETED`.

---

## 8. Salidas generadas

### En base de datos (PostgreSQL)

| Tabla | Contenido |
|---|---|
| `transaccion` | Transacciones migradas y normalizadas, con marca de anomalía |
| `resumen_diario` | Reporte diario del Job 1 |
| `cuenta_interes` | Saldo inicial, tasa, interés y saldo final por cuenta |
| `movimiento_anual` | Movimientos anuales con signo y descripción normalizados |
| `estado_cuenta_anual` | Estado de cuenta compilado por cuenta y año |
| `registro_rechazado` | Bitácora de todo lo omitido o filtrado, con línea y motivo |
| `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`, … | Metadatos de Spring Batch |

Todas las tablas de negocio llevan `job_execution_id`, de modo que cada corrida queda
aislada y se puede comparar una migración contra otra.

### En archivos (carpeta `salida/`)

| Archivo | Contenido |
|---|---|
| `reporte_transacciones_diarias.csv` | Un registro por día con totales y anomalías |
| `intereses_mensuales.csv` | Detalle liquidado por cuenta |
| `estados_cuenta_anuales.csv` | Informe anual por cuenta para auditoría |
| `rechazados_<job>.csv` | Filas descartadas con su línea, motivo y contenido original |

---

## 9. Resultados de ejecución

Corridas reales contra PostgreSQL (`evidencias/logs/`):

| Corrida | Dataset | Leídos | Escritos | Omitidos | Filtrados | Estado |
|---|---|---|---|---|---|---|
| Job 1 — transacciones | `semana_1` | 10 | 10 | 0 | 0 | COMPLETED |
| Job 2 — intereses | `semana_1` | 8 | 8 | 0 | 0 | COMPLETED |
| Job 3 — estados de cuenta | `semana_1` | 9 | 9 | 0 | 0 | COMPLETED |
| Migración completa | `semana_2` | 27 | 22 | 5 | 0 | COMPLETED |
| Migración completa | `semana_3` | 3.000 | 1.770 | 1.221 | 9 | COMPLETED |
| Job 1 con fallo simulado | `semana_1` | 10 | 10 | 0 | 0 | COMPLETED (1 rollback) |

Los «leídos» son las filas que entraron al pipeline y los «escritos» las que quedaron
migradas; los omitidos y filtrados suman exactamente la diferencia, y cada uno de ellos está
en la bitácora con su archivo, su línea y su motivo.

Sobre `semana_3` (1.000 filas por archivo, el caso más degradado), el Job 1 migró 491
transacciones: corrigió 374 fechas en formato legacy y marcó 166 con alguna anomalía. Las
marcas no son excluyentes —dos filas acumulan dos— y se reparten en 90 por monto no positivo,
63 por monto atípico y 15 por posible duplicado. Las 509 filas restantes quedaron en la
bitácora con su motivo: 294 por tipo desconocido, 160 por monto vacío y 55 por fecha
inexistente.

El Job 3 sobre el mismo dataset migró 952 de 1.000 movimientos: las 48 omisiones son todas por
monto vacío, el único defecto de ese archivo que no se puede corregir sin inventar el valor.

Para reproducir las consultas de evidencia:

```bash
docker exec -i banco-xyz-db psql -U banco -d banco_xyz -f - < evidencias/consultas_evidencia.sql
```

---

## 10. Pruebas

```bash
./mvnw test
```

**99 pruebas, cobertura 96,4 % de instrucciones y 84,5 % de ramas.**

| Tipo | Clases | Qué cubren |
|---|---|---|
| Unitarias de parseo | `ParseadorFechasTest`, `ParseadorNumerosTest`, `EscritorCsvTest` | Los 4 formatos de fecha, resolución estricta, montos vacíos, normalización de tildes, escape CSV |
| Unitarias de negocio | `TransaccionItemProcessorTest`, `InteresItemProcessorTest`, `MovimientoAnualItemProcessorTest` | Cada regla de validación, corrección y clasificación, en éxito y en error |
| Unitarias de infraestructura | `DetectorDeDuplicadosTest`, `PoliticaOmisionBancariaTest`, `LanzadorDeJobsTest` | Reproceso vs duplicado, qué se omite y qué detiene, argumentos y códigos de salida |
| Entidades | `EntidadesDestinoTest` | Accesores, identidad (`equals`/`hashCode`) y recorte de campos |
| Integración de Jobs | `JobsDeMigracionIT` | Los 4 Jobs de extremo a extremo sobre H2, con contadores y archivos verificados |
| Integración de reintento | `ReintentoAnteFalloTransitorioIT` | Rollback + reintento + `COMPLETED` sin perder registros |

---

## 11. Decisiones de diseño y sus motivos

| Decisión | Motivo |
|---|---|
| DTO de entrada con todos los campos en `String` | Si el lector exigiera tipos, reventaría en la primera fila sucia y se perdería la trazabilidad del dato |
| `ItemProcessor` separado del `ItemReader` | La validación de negocio es responsabilidad del procesador; el lector solo debe conseguir que la fila entre al pipeline |
| Bitácora en transacción `REQUIRES_NEW` | Si participara del chunk, el rollback se llevaría justo el registro del error que se quiere auditar |
| Identificadores por `SEQUENCE` con `allocationSize=50` | Permite que Hibernate agrupe los INSERT en lotes JDBC; con `IDENTITY` habría un viaje a la base por fila |
| Agregaciones paginadas en los `Tasklet` | Cargar la corrida completa en memoria funciona con 1.000 filas, no con el volumen real del banco |
| Tasas y umbrales en `application.properties` | El área de riesgo los ajusta sin recompilar |
| `split()` con `SimpleAsyncTaskExecutor` en `migracionCompletaJob` | Los tres archivos son independientes: el tiempo total pasa a ser el del proceso más lento y no la suma de los tres |
| `spring.batch.job.enabled=false` + lanzador propio | El banco necesita correr el cierre de intereses sin reprocesar las transacciones del día |

---

## 12. Estructura del repositorio

```
banco-xyz-batch/
├── data/                     CSV legacy (semana_1, semana_2, semana_3)
├── docker-compose.yml        PostgreSQL 17 en el puerto 5433
├── evidencias/
│   ├── consultas_evidencia.sql   Consultas de verificación sobre PostgreSQL
│   ├── logs/                     Salida de consola de las 6 corridas
│   └── img/                      Capturas de las evidencias
├── salida/                   Archivos CSV generados por los Jobs
├── src/main/java/com/bancoxyz/
├── src/main/resources/       application.properties · banner.txt
├── src/test/java/com/bancoxyz/
├── src/test/resources/       Perfil H2 y juego de datos de prueba
└── pom.xml
```

---

## 13. Autor

**Diego Carvajal** — Analista Programador, Duoc UC
Desarrollo Backend III (PBY2203) · Experiencia 1, Semana 1 · Grupo 18
GitHub: [@dcarvajal99](https://github.com/dcarvajal99)
