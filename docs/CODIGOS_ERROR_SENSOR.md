# Códigos de error — FreeStyle LibreLink (GB) 2.13.1

Este documento resume **todos los códigos de error identificados** en el código
decompilado (ver [MANUAL_DECOMPILACION.md](MANUAL_DECOMPILACION.md)), tanto los del
**sensor de glucosa** (p. ej. `365P`, `365K`, `365I`, `365C`) como los del **subsistema de
plumas de insulina conectadas (Pen Abstraction Service)**. Para cada error se indica el
fichero fuente exacto donde localizarlo tras decompilar.

## Índice

1. [Cómo se construye el código del sensor](#1-cómo-se-construye-el-código-mostrado)
2. [Códigos base del sensor (3 dígitos)](#2-tabla-de-códigos-base-3-dígitos)
3. [Letras de causa extendida (A–T)](#3-tabla-de-letras-finales-causa-técnica-extendida-at)
4. [Ejemplos 365P / 365K / 365I / 365C](#4-códigos-concretos-solicitados-365p-365k-365i-365c)
5. [Máscara de bits `DataQuality` (origen de la letra extendida)](#5-máscara-de-bits-dataquality-origen-de-la-letra-extendida)
6. [Excepciones Java por condición de sensor](#6-excepciones-java-asociadas-a-cada-condición-de-sensor)
7. [Errores de las plumas de insulina (Pen Abstraction Service)](#7-errores-de-las-plumas-de-insulina-pen-abstraction-service)
8. [Referencias — todos los ficheros fuente](#8-referencias-a-los-ficheros-fuente-usados)

## 1. Cómo se construye el código mostrado

El código que ves en pantalla (`365P`, `365K`, ...) se compone de **dos partes** definidas
en clases distintas del binario:

1. **Código base (3 dígitos)** — definido en el enum
   [`com.librelink.app.types.ErrorCondition`](../) (fichero decompilado
   `jadx_output/sources/com/librelink/app/types/ErrorCondition.java`). Representa la
   *condición de error* general del sensor (terminado, corrupto, en calentamiento, etc.).
2. **Letra final (A–T)** — definida en el enum
   `com.abbottdiabetescare.flashglucose.sensorabstractionservice.ExtendedError`
   (fichero `jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/ExtendedError.java`).
   Representa la *causa técnica exacta* detectada por el chip del sensor (calidad de la
   señal, voltaje, fugas, etc.) que provocó que la condición base se disparara.

Es decir: `365` + `P` = "el sensor se ha dado de baja (`TERMINATED`, código 365) porque el
motivo técnico concreto fue `SERR_INVALID_DATA` (letra `P`)".

Extracto real del código decompilado que define el enum de letras:

```java
// com/abbottdiabetescare/flashglucose/sensorabstractionservice/ExtendedError.java
@Copyright("Copyright (c) 2024 Abbott Group of Companies. All rights reserved. STRICTLY CONFIDENTIAL.")
public enum ExtendedError {
    SERR_SD14_FIFO_OVERFLOW(65, 'A'),
    SERR_FILTER_DELTA(66, 'B'),
    SERR_WORK_VOLTAGE(67, 'C'),
    SERR_PEAK_DELTA_EXCEEDED(68, 'D'),
    SERR_AVG_DELTA_EXCEEDED(69, 'E'),
    SERR_RF(70, 'F'),
    SERR_REF_R(71, 'G'),
    SERR_SIGNAL_SATURATED(72, 'H'),
    SERR_SENSOR_SIGNAL_LOW(73, 'I'),
    SERR_RESERVED_1(74, 'J'),
    SERR_LEAK_DETECTED(75, 'K'),
    SERR_THERMISTOR_OUT_OF_RANGE(76, 'L'),
    SERR_RESERVED_3(77, 'M'),
    SERR_RESERVED_TEMP_HIGH(78, 'N'),
    SERR_RESERVED_TEMP_LOW(79, 'O'),
    SERR_INVALID_DATA(80, 'P'),
    SERR_INVALID_PATCH_DATA_INDICES(81, 'Q'),
    SERR_ERROR_TERMINATION_STATE(82, 'R'),
    SERR_WRONG_STATE(83, 'S'),
    SERR_UNKNOWN_STATE(84, 'T'),
    LAST_EXTENDED_SENSOR_ERROR_CODE(84, 'T');
    ...
}
```

Y el punto donde se calcula qué letra corresponde según el bit de calidad de dato
(`DataQuality`) devuelto por el sensor (`DefaultSensorAbstractionService.java`,
método `convertDataQualityCodeToExtendedErrorCode`):

```java
private int convertDataQualityCodeToExtendedErrorCode(int i) {
    ExtendedError extendedError;
    if ((DataQuality.SD14_FIFO_OVERFLOW.getValue() & i) != 0) extendedError = ExtendedError.SERR_SD14_FIFO_OVERFLOW;
    else if ((DataQuality.FILTER_DELTA.getValue() & i) != 0) extendedError = ExtendedError.SERR_FILTER_DELTA;
    else if ((DataQuality.WORK_VOLTAGE.getValue() & i) != 0) extendedError = ExtendedError.SERR_WORK_VOLTAGE;
    else if ((DataQuality.PEAK_DELTA_EXCEEDED.getValue() & i) != 0) extendedError = ExtendedError.SERR_PEAK_DELTA_EXCEEDED;
    else if ((DataQuality.AVG_DELTA_EXCEEDED.getValue() & i) != 0) extendedError = ExtendedError.SERR_AVG_DELTA_EXCEEDED;
    else if ((DataQuality.RF.getValue() & i) != 0) extendedError = ExtendedError.SERR_RF;
    else if ((DataQuality.REF_R.getValue() & i) != 0) extendedError = ExtendedError.SERR_REF_R;
    else if ((DataQuality.SIGNAL_SATURATED.getValue() & i) != 0) extendedError = ExtendedError.SERR_SIGNAL_SATURATED;
    else if ((DataQuality.SENSOR_SIGNAL_LOW.getValue() & i) != 0) extendedError = ExtendedError.SERR_SENSOR_SIGNAL_LOW;
    else if ((DataQuality.THERMISTOR_OUT_OF_RANGE.getValue() & i) != 0) extendedError = ExtendedError.SERR_THERMISTOR_OUT_OF_RANGE;
    else if ((DataQuality.TEMP_HIGH.getValue() & i) != 0) extendedError = ExtendedError.SERR_RESERVED_TEMP_HIGH;
    else if ((DataQuality.TEMP_LOW.getValue() & i) != 0) extendedError = ExtendedError.SERR_RESERVED_TEMP_LOW;
    else if ((DataQuality.LEAK_DETECTED.getValue() & i) != 0) extendedError = ExtendedError.SERR_LEAK_DETECTED;
    else extendedError = ExtendedError.SERR_INVALID_DATA;
    return extendedError.getCode();
}
```

## 2. Tabla de códigos base (3 dígitos)

Extraído del enum `ErrorCondition` (constructor `(nombre, ordinal, isStreaming, errorCode,
sasErrorCode, tituloRes, ...)`), junto con el título de recurso `strings.xml` asociado:

| Código | Constante interna | Título mostrado (`strings.xml`) | Significado |
|---|---|---|---|
| 335 | `TRANSMISSION_ERROR` | Scan Error | Fallo de transmisión NFC/BLE al leer el sensor; repetir el escaneo. |
| 336 | `NOT_ACTIVE` | New Sensor Found | Sensor nuevo detectado, aún no activado/emparejado. |
| 337 | `EXPIRED` | Sensor Ended | El sensor ha llegado al final de su vida útil (14 días). |
| 338 | `IN_WARMUP` | Please wait | Sensor en periodo de calentamiento inicial (~60 min). |
| 365 | `TERMINATED` | Replace Sensor | El sensor se ha autodesactivado por seguridad (fallo interno de calidad de datos). **Este es el código de tu pregunta (365x).** |
| 366 | `INSERTION_FAILURE` | Check Sensor | Fallo de inserción del sensor detectado. |
| 367 | `NOT_COMPATIBLE` | Incompatible Sensor | Sensor no compatible con esta app/región. |
| 372 | `ALREADY_STARTED` | Sensor Already in Use | El sensor ya fue activado por otro dispositivo. |
| 373 | `TEMPORARY_PROBLEM` | Glucose Readings Paused | Problema temporal, la lectura se reanuda sola. |
| 374 | `TEMP_HIGH` | Sensor Too Hot | Temperatura del sensor demasiado alta. |
| 375 | `TEMP_LOW` | Sensor Too Cold | Temperatura del sensor demasiado baja. |
| 376 | `NOT_COMPATIBLE_C` | Incompatible Sensor | Variante de incompatibilidad (región/hardware). |
| 379 | `RESPONSE_CORRUPT` | Scan Error | Respuesta NFC corrupta/ilegible; repetir el escaneo. |
| 380 | `EARLY_ATTENUATION` | Glucose Readings Paused | Atenuación temprana de señal tras insertar el sensor. |

## 3. Tabla de letras finales (causa técnica extendida, A–T)

Extraído del enum `ExtendedError` (`código numérico interno`, `letra mostrada`):

| Letra | Constante interna | Significado técnico |
|---|---|---|
| A | `SERR_SD14_FIFO_OVERFLOW` | Desbordamiento del buffer FIFO del front-end analógico del sensor. |
| B | `SERR_FILTER_DELTA` | El filtro de señal detecta un salto (delta) fuera de rango. |
| C | `SERR_WORK_VOLTAGE` | Voltaje de trabajo del electrodo fuera de los límites válidos. |
| D | `SERR_PEAK_DELTA_EXCEEDED` | Pico de variación de señal excede el umbral permitido. |
| E | `SERR_AVG_DELTA_EXCEEDED` | Variación media de señal excede el umbral permitido. |
| F | `SERR_RF` | Error de radiofrecuencia (comunicación NFC/BLE). |
| G | `SERR_REF_R` | Error en la resistencia/electrodo de referencia. |
| H | `SERR_SIGNAL_SATURATED` | Señal del sensor saturada (fuera de rango de medida). |
| I | `SERR_SENSOR_SIGNAL_LOW` | Señal del sensor demasiado baja/débil. |
| J | `SERR_RESERVED_1` | Código reservado (sin uso documentado en esta versión). |
| K | `SERR_LEAK_DETECTED` | Fuga detectada en el sensor (humedad/contacto). |
| L | `SERR_THERMISTOR_OUT_OF_RANGE` | Termistor (sensor de temperatura) fuera de rango. |
| M | `SERR_RESERVED_3` | Código reservado (sin uso documentado en esta versión). |
| N | `SERR_RESERVED_TEMP_HIGH` | Reservado para condición de temperatura alta. |
| O | `SERR_RESERVED_TEMP_LOW` | Reservado para condición de temperatura baja. |
| P | `SERR_INVALID_DATA` | Datos inválidos/no reconocidos recibidos del sensor. |
| Q | `SERR_INVALID_PATCH_DATA_INDICES` | Índices de datos del parche (patch) inválidos. |
| R | `SERR_ERROR_TERMINATION_STATE` | El sensor entró en estado de terminación por error. |
| S | `SERR_WRONG_STATE` | El sensor está en un estado inesperado para la operación solicitada. |
| T | `SERR_UNKNOWN_STATE` | Estado del sensor desconocido/no reconocido (también marca el último código soportado). |

## 4. Códigos concretos solicitados: 365P, 365K, 365I, 365C

Combinando la tabla base (§2) con la de letras (§3):

| Código completo | Condición base | Causa técnica extendida | Explicación para el usuario |
|---|---|---|---|
| **365P** | `TERMINATED` (365, "Replace Sensor") | `SERR_INVALID_DATA` (P) | El sensor se ha desactivado por seguridad porque envió datos inválidos que no pudieron interpretarse correctamente. Hay que retirarlo e iniciar uno nuevo. |
| **365K** | `TERMINATED` (365, "Replace Sensor") | `SERR_LEAK_DETECTED` (K) | El sensor se ha desactivado por seguridad porque detectó una fuga (posible entrada de humedad/fluido). Hay que retirarlo e iniciar uno nuevo. |
| **365I** | `TERMINATED` (365, "Replace Sensor") | `SERR_SENSOR_SIGNAL_LOW` (I) | El sensor se ha desactivado por seguridad porque la señal eléctrica del electrodo era demasiado débil para dar lecturas fiables. Hay que retirarlo e iniciar uno nuevo. |
| **365C** | `TERMINATED` (365, "Replace Sensor") | `SERR_WORK_VOLTAGE` (C) | El sensor se ha desactivado por seguridad porque el voltaje de trabajo del electrodo estaba fuera de rango. Hay que retirarlo e iniciar uno nuevo. |

En los cuatro casos, el mensaje que ve el usuario en la app es el mismo (texto del recurso
`error_sensor_terminated_msg`):

> "Your Sensor is not working. Please remove your Sensor and start a new one."

La letra final (`P`, `K`, `I`, `C`, ...) es información de diagnóstico interno para
soporte técnico de Abbott; no cambia la acción que debe tomar el usuario (sustituir el
sensor), solo indica **por qué** el firmware del sensor decidió autodesactivarse.

## 5. Máscara de bits `DataQuality` (origen de la letra extendida)

El sensor devuelve un entero de calidad de dato (bitmask). `DataQuality` define cada bit,
y `DefaultSensorAbstractionService.convertDataQualityCodeToExtendedErrorCode(int)` recorre
los bits **en este orden de prioridad** (el primero que coincide gana) para decidir la
letra final de `ExtendedError` (ver bloque de código en §1).

**Fuente:** `jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/DataQuality.java`

| Bit (valor) | Constante `DataQuality` | Letra `ExtendedError` resultante |
|---|---|---|
| 1 | `SD14_FIFO_OVERFLOW` | A |
| 2 | `FILTER_DELTA` | B |
| 4 | `WORK_VOLTAGE` | C |
| 8 | `PEAK_DELTA_EXCEEDED` | D |
| 16 | `AVG_DELTA_EXCEEDED` | E |
| 32 | `RF` | F |
| 64 | `REF_R` | G |
| 128 | `SIGNAL_SATURATED` | H |
| 256 | `SENSOR_SIGNAL_LOW` | I |
| 512 | `LEAK_DETECTED` | K |
| 2048 | `THERMISTOR_OUT_OF_RANGE` | L |
| 8192 | `TEMP_HIGH` | N (reservado) |
| 16384 | `TEMP_LOW` | O (reservado) |
| 32768 | `INVALID_DATA` | P (valor por defecto si ningún otro bit coincide) |
| 65535 | `HISTORIC_DATA_NEVER_CALCULATED` | — (sin dato histórico calculado todavía) |
| 0 | `OK` (`DataQuality.OK`) | Sin error, dato válido |

## 6. Excepciones Java asociadas a cada condición de sensor

Cada valor de `ErrorCondition` (§2) tiene una excepción Java específica que se lanza en el
SDK del sensor. Localización: carpeta
`jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/`.

| Fichero (`.java`) | Condición relacionada (código) |
|---|---|
| `SensorRfTransmissionErrorException.java` | `TRANSMISSION_ERROR` (335) |
| `SensorNotActiveException.java` | `NOT_ACTIVE` (336) |
| `SensorExpiredException.java` | `EXPIRED` (337) |
| `SensorInWarmupException.java` | `IN_WARMUP` (338) |
| `SensorTerminatedException.java` | `TERMINATED` (365) |
| `SensorInsertionFailureException.java` | `INSERTION_FAILURE` (366) |
| `SensorNotCompatibleException.java` | `NOT_COMPATIBLE` (367) |
| `SensorAlreadyStartedException.java` | `ALREADY_STARTED` (372) |
| `SensorTemporaryProblemException.java` | `TEMPORARY_PROBLEM` (373) |
| `SensorTemperatureTooHighException.java` | `TEMP_HIGH` (374) |
| `SensorTemperatureTooLowException.java` | `TEMP_LOW` (375) |
| `SensorNotCompatibleVitaminCException.java` | `NOT_COMPATIBLE_C` (376) |
| `SensorResponseCorruptException.java` | `RESPONSE_CORRUPT` (379) |
| `SensorEarlyAttenuationException.java` | `EARLY_ATTENUATION` (380) |
| `DatabaseCorruptException.java` | Corrupción de la base de datos local (SQLite/ORMLite), no es un código de `ErrorCondition`, sino un fallo de integridad de datos en el propio teléfono. |
| `MathFailureException.java` | Fallo aritmético interno al calcular la curva de glucosa (protección "safety critical"). |
| `SafetyCriticalException.java` | Excepción base marcada como crítica de seguridad; las excepciones anteriores heredan de ella. |

Las clases de la tabla de errores persistidos en base de datos (para consultar histórico)
están en la misma carpeta, subcarpeta `database/`:
`CurrentErrorEntity.java`, `HistoricErrorEntity.java`, `RealTimeErrorEntity.java`
(todas guardan el campo `dataQuality`, el bitmask crudo de §5, junto con sensor y timestamp).

## 7. Errores de las plumas de insulina (Pen Abstraction Service)

Además de los errores del sensor de glucosa, la app integra un SDK para plumas de
insulina conectadas (NovoPen y similares). Sus códigos de error viven en
`jadx_output/sources/com/freestylelibre/penabstractionservice/constants/`.

### 7.1 `PenScanErrors.java` — errores al escanear/emparejar una pluma

**Fuente:** `PenScanErrors.java` (mismo paquete indicado arriba)

| Constante | Significado |
|---|---|
| `NONE` | Sin error. |
| `INVALID_PEN_MODEL` | Modelo de pluma no reconocido/soportado. |
| `SEGMENT` | Error leyendo un segmento de datos NFC de la pluma. |
| `UNKNOWN` | Error desconocido durante el escaneo. |
| `TAG_LOST` | Se perdió el contacto NFC con la pluma durante la lectura. |
| `DOSE_ERROR` | Error al leer el registro de dosis de la pluma (ver §7.2). |
| `NO_PENS` | No hay ninguna pluma emparejada/registrada. |
| `PEN_NOT_SETUP` | La pluma no ha completado el proceso de configuración inicial. |
| `UNSUPPORTED_FIRMWARE_VERSION` | Firmware de la pluma no soportado por esta versión de la app. |
| `ALREADY_REGISTERED` | La pluma ya está registrada en otra cuenta/dispositivo. |

### 7.2 `PenDoseErrors.java` — errores de memoria de dosis de la pluma

**Fuente:** `PenDoseErrors.java` (constructor `(prioridad, prioridadOrigen, orden)`, valores
internos de prioridad usados para decidir qué error mostrar cuando hay varios activos).

| Constante | Significado |
|---|---|
| `DEFAULT` | Sin error / valor por defecto. |
| `ST_WARNING_EOL` | Aviso de fin de vida útil (batería/memoria) de la pluma. |
| `ST_SENSOR_ERR` | Error del sensor interno de dosis de la pluma. |
| `ST_INTERRUPTED_DOSE` | La inyección se interrumpió antes de completarse. |
| `ST_RECOVERABLE_ERR` | Error recuperable en la memoria de dosis. |
| `ST_UNRECOVERABLE_ERR` | Error irrecuperable en la memoria de dosis. |
| `ST_BIG_DOSE` | Dosis registrada anómalamente grande (posible error de lectura). |
| `ST_CRC_CORRUPTED` | Fallo de checksum/CRC en los datos de dosis leídos. |
| `ST_EXP_END_OF_LIFE` | La pluma ha llegado a su fin de vida / caducidad. |
| `ST_DOSE_IN_PROGRESS` | Hay una dosis en curso en el momento de la lectura. |

Los textos mostrados al usuario para estos errores de pluma están en
`apktool_output/res/values/strings.xml` bajo las claves `novo_error_*`
(por ejemplo `novo_error_doseMemory_message`, `novo_error_scanFailed_message`,
`novo_error_doseMemoryTechnical_messageSecondary`).

## 8. Referencias a los ficheros fuente usados

| Sección | Fichero(s) fuente (tras decompilar con `jadx`/`apktool`) |
|---|---|
| §1–§2 (códigos base 3 dígitos) | `jadx_output/sources/com/librelink/app/types/ErrorCondition.java` |
| §1, §3 (letras A–T) | `jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/ExtendedError.java` |
| §1, §5 (cálculo letra desde bitmask) | `jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/DefaultSensorAbstractionService.java` (método `convertDataQualityCodeToExtendedErrorCode`) |
| §5 (bitmask `DataQuality`) | `jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/DataQuality.java` |
| §6 (excepciones por condición) | `jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/Sensor*Exception.java`, `DatabaseCorruptException.java`, `MathFailureException.java`, `SafetyCriticalException.java` |
| §6 (persistencia de errores en BD) | `jadx_output/sources/com/abbottdiabetescare/flashglucose/sensorabstractionservice/database/{CurrentErrorEntity,HistoricErrorEntity,RealTimeErrorEntity}.java` |
| §7.1 (errores de escaneo de pluma) | `jadx_output/sources/com/freestylelibre/penabstractionservice/constants/PenScanErrors.java` |
| §7.2 (errores de dosis de pluma) | `jadx_output/sources/com/freestylelibre/penabstractionservice/constants/PenDoseErrors.java` |
| Textos mostrados al usuario | `apktool_output/res/values/strings.xml` (claves `error_sensor_*` líneas ~638–680, claves `novo_error_*` líneas ~1000–1030) |
| Registro genérico de errores de la app (analítica/crash) | `jadx_output/sources/com/librelink/app/database/AppErrorEntity.java` y `jadx_output/sources/com/librelink/app/services/EventLogService.java` |

Todo generado siguiendo los pasos descritos en
[MANUAL_DECOMPILACION.md](MANUAL_DECOMPILACION.md).
