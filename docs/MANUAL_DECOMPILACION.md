# Manual de decompilación — FreeStyle LibreLink (GB) 2.13.1

Este manual documenta, paso a paso y con todos los comandos ejecutables, el proceso
seguido para decompilar el fichero `com.freestylelibre.app.gb_2.13.1-11596_4arch_7dpi_24.apkm`
y extraer sus clases Java/Kotlin, recursos y smali. Puedes copiar y pegar los bloques de
código en una terminal (macOS/Linux, zsh o bash) para reproducirlo exactamente.

> **Nota legal:** Este análisis se realiza con fines educativos y de interoperabilidad
> personal (entender los códigos de error mostrados por la app). El paquete decompilado
> no se redistribuye; solo se documentan hechos técnicos (constantes, strings, enums)
> encontrados en el propio binario.

## 0. Datos del paquete analizado

| Campo | Valor |
|---|---|
| Nombre de la app | FreeStyle LibreLink - GB |
| Package (applicationId) | `com.freestylelibre.app.gb` |
| Versión | 2.13.1 |
| Version code | 11596 |
| min SDK | 26 (Android 8.0) |
| Formato del fichero | `.apkm` (APKMirror Installer — bundle ZIP con `base.apk` + splits) |

Estos datos proceden del `info.json` incluido dentro del propio `.apkm`.

## 1. Requisitos previos

Herramientas usadas (instaladas vía Homebrew en macOS):

```bash
brew install jadx apktool
```

Versiones utilizadas en este manual:

```bash
jadx --version      # 1.5.5
apktool --version   # 3.0.2
java -version       # OpenJDK 25 (Temurin) — cualquier JDK 11+ sirve
unzip -v            # unzip de macOS (BSD) o GNU unzip en Linux
```

## 2. El formato `.apkm`

Un `.apkm` es simplemente un ZIP que contiene un `base.apk` y varios `split_config.*.apk`
(uno por arquitectura de CPU, densidad de pantalla e idioma), más un `info.json` con
metadatos. El código Java/Kotlin de la aplicación está siempre en `base.apk`; los splits
solo llevan recursos (imágenes, traducciones) y librerías nativas (`.so`) por arquitectura.

### 2.1 Extraer el `.apkm`

```bash
mkdir -p /tmp/libre_decompile
cd /tmp/libre_decompile

unzip -o "/Users/antonio-bravo/AndroidStudioProjects/GlucoseApp/com.freestylelibre.app.gb_2.13.1-11596_4arch_7dpi_24.apkm" \
  -d apkm_extracted

ls -la apkm_extracted
```

Esto genera, entre otros:

```
apkm_extracted/base.apk                     <- APK principal (código + recursos comunes)
apkm_extracted/split_config.arm64_v8a.apk   <- librerías nativas ARM64
apkm_extracted/split_config.armeabi_v7a.apk
apkm_extracted/split_config.x86.apk
apkm_extracted/split_config.x86_64.apk
apkm_extracted/split_config.<dpi>.apk       <- recursos por densidad
apkm_extracted/split_config.<lang>.apk      <- strings traducidos por idioma
apkm_extracted/info.json                    <- metadatos (versión, package, etc.)
```

## 3. Decompilar el código a Java con `jadx`

`jadx` convierte el bytecode DEX de `base.apk` en código fuente Java legible (con nombres
ofuscados tipo `C1817dT`, típico de R8/ProGuard).

```bash
cd /tmp/libre_decompile
jadx -d jadx_output apkm_extracted/base.apk
```

Salida esperada (algunos errores son normales en apps grandes con clases sintéticas y no
impiden obtener el resto del código fuente):

```
INFO  - loading ...
INFO  - processing ...
ERROR - finished with errors, count: 78
```

Verificación del resultado:

```bash
find jadx_output/sources -name '*.java' | wc -l
# 10586 ficheros .java generados

find jadx_output/sources/com/freestylelibre -maxdepth 2 -type d
find jadx_output/sources/com/librelink -maxdepth 2 -type d
find jadx_output/sources/com/abbottdiabetescare -maxdepth 2 -type d
```

Los paquetes relevantes de la app (no librerías de terceros) son:

- `com.freestylelibre.app.gb` — clases de arranque / manifest de esta marca del producto.
- `com.librelink.app` — lógica principal de la app (UI, base de datos, servicios, tipos).
- `com.abbottdiabetescare.flashglucose.sensorabstractionservice` — SDK de comunicación
  con el sensor (Sensor Abstraction Service), donde viven los códigos de error del sensor.
- `com.freestylelibre.penabstractionservice` — SDK de comunicación con plumas de insulina
  conectadas (Pen Abstraction Service).
- `defpackage.*` — clases con nombres ofuscados por R8 (mayoría del código de terceros
  y parte del propio, sin paquete real).

## 4. Decompilar recursos y `AndroidManifest.xml` con `apktool`

`jadx` también decodifica recursos, pero `apktool` es más fiable para recuperar
`AndroidManifest.xml`, `strings.xml`, layouts XML y smali completo (incluyendo IDs de
recursos sin resolver).

```bash
cd /tmp/libre_decompile
apktool d -f -o apktool_output apkm_extracted/base.apk
```

Salida esperada (los avisos `W: Unresolved resource reference` son inofensivos, ocurren
por recursos definidos en los splits que no están presentes en `base.apk`):

```
I: Using Apktool 3.0.2 on base.apk
I: Loading resource table...
...
I: Decoding AndroidManifest.xml with resources...
I: Copying original files...
I: Copying assets...
I: Copying unknown files...
```

Resultado relevante:

```
apktool_output/AndroidManifest.xml     <- Manifest completo y legible
apktool_output/res/values/strings.xml  <- Todos los textos de la app (~1300+ strings)
apktool_output/res/values/public.xml   <- Tabla de IDs de recursos (R.string.X = 0x7f...)
apktool_output/smali/                  <- Bytecode Dalvik en smali (para clases que jadx no pudo restaurar)
```

## 5. (Opcional) Decompilar también los splits

Si se necesitan recursos específicos de idioma/densidad (por ejemplo `strings.xml` en
español), se decompila cada split igual que el base:

```bash
cd /tmp/libre_decompile
apktool d -f -o apktool_es apkm_extracted/split_config.es.apk
jadx -d jadx_es apkm_extracted/split_config.es.apk
```

## 6. Localizar todas las clases extraídas

```bash
# Listado completo de clases del paquete propio de la app
find jadx_output/sources/com/librelink -name '*.java' | sort > /tmp/libre_decompile/clases_librelink.txt
find jadx_output/sources/com/abbottdiabetescare -name '*.java' | sort > /tmp/libre_decompile/clases_sensor_service.txt
find jadx_output/sources/com/freestylelibre -name '*.java' | sort > /tmp/libre_decompile/clases_freestylelibre.txt

wc -l /tmp/libre_decompile/clases_*.txt
```

Todas las clases del `.dex` (incluidas las de librerías de terceros) están bajo
`jadx_output/sources/`, respetando la estructura de paquetes original salvo las clases
ofuscadas por R8, que aparecen sueltas en el paquete raíz `defpackage/`.

## 7. Búsquedas útiles sobre el código ya decompilado

```bash
cd /tmp/libre_decompile

# Buscar todas las clases relacionadas con errores/condiciones
grep -rliE 'error.?code|errorcode' jadx_output/sources/com/librelink jadx_output/sources/com/abbottdiabetescare

# Buscar un string de recurso concreto
grep -n 'error_sensor_terminated' apktool_output/res/values/strings.xml

# Buscar referencias cruzadas a una clase concreta en todo el código
grep -rn 'ExtendedError\.' jadx_output/sources/com
```

## 8. Resumen de comandos (todo en uno)

Bloque único para reproducir el proceso completo desde cero:

```bash
set -e
APKM="/Users/antonio-bravo/AndroidStudioProjects/GlucoseApp/com.freestylelibre.app.gb_2.13.1-11596_4arch_7dpi_24.apkm"
WORKDIR="/tmp/libre_decompile"

mkdir -p "$WORKDIR"
cd "$WORKDIR"

# 1. Extraer el bundle .apkm
unzip -o "$APKM" -d apkm_extracted

# 2. Decompilar código Java/Kotlin
jadx -d jadx_output apkm_extracted/base.apk

# 3. Decompilar recursos + manifest + smali
apktool d -f -o apktool_output apkm_extracted/base.apk

echo "Listo. Código en $WORKDIR/jadx_output/sources, recursos en $WORKDIR/apktool_output/res"
```

## 9. Herramientas alternativas (no usadas aquí, mencionadas por completitud)

- `dex2jar` + `jd-gui` / `cfr` / `procyon`: convertir `classes.dex` a `.jar` y luego a
  Java con un decompilador standalone.
- `bytecode-viewer`: GUI que integra varios decompiladores.
- Android Studio: `Analyze > Inspect Code` / arrastrar el `.apk` directamente al editor
  para ver bytecode y un Java aproximado.

Para este manual se ha optado por `jadx` (mejor legibilidad del código Kotlin/Java) y
`apktool` (mejor fidelidad en recursos y manifest), que es la combinación estándar.
