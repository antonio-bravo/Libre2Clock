# Decompilacion APK y verificacion de endpoint historico

Este documento resume, en orden, el proceso usado para investigar si la app oficial contiene un endpoint claro para extraer historico de glucosa (ej. 90 dias), incluyendo los comandos usados y la interpretacion tecnica.

## 1) Objetivo de la investigacion

- Confirmar si existe un endpoint visible para descargar historico de glucosa.
- Diferenciar entre:
  - Flujo de subida (upload)
  - Flujo de lectura/descarga (download)
- Identificar hosts, rutas, clientes HTTP y configuraciones relacionadas con sincronizacion.

## 2) Prerrequisitos

Herramientas comprobadas en macOS:

```bash
command -v jadx && command -v apktool && command -v unzip && command -v aapt
```

Nota: en esta sesion, rg no estaba disponible, por eso se uso grep/find.

## 3) Preparacion y extraccion del .apkm

Se localizo el archivo .apkm y se extrajo a una carpeta temporal.

```bash
rm -rf tmp_apkm_inspect && mkdir -p tmp_apkm_inspect && unzip -q com.freestylelibre.app.gb_2.13.1-11596_4arch_7dpi_24.apkm -d tmp_apkm_inspect && find tmp_apkm_inspect -maxdepth 2 -type f | sed 's#^#/#' | head -n 200
```

Comprobacion inicial de cadenas en el paquete extraido:

```bash
grep -aRniE "api\.libreview\.io|libreview|graph|history|report|stat|insight|logbook|retrofit2\.http|okhttp3|https://" tmp_apkm_inspect/base.apk tmp_apkm_inspect/info.json tmp_apkm_inspect 2>/dev/null | head -n 200
```

## 4) Decompilacion del APK base

Decompilacion con JADX:

```bash
rm -rf tmp_jadx && jadx -q -d tmp_jadx tmp_apkm_inspect/base.apk && find tmp_jadx/sources -maxdepth 4 -type f | head -n 50
```

Verificacion de estructura de salida:

```bash
find tmp_jadx -maxdepth 3 -type d | head -n 50 && find tmp_jadx/sources -type f | wc -l
```

## 5) Busqueda de endpoints y clientes HTTP en codigo decompilado

Busqueda general de anotaciones HTTP, Retrofit, URLs y palabras clave:

```bash
grep -RniE "@GET|@POST|Retrofit|OkHttp|baseUrl|https://|libreview|graph|history|report|stat|insight|logbook" tmp_jadx/sources 2>/dev/null | head -n 250
```

Busquedas orientadas a posibles capas no evidentes:

```bash
grep -RniE "HttpURLConnection|URLConnection|Request\.Builder|OkHttpClient|openConnection\(|setRequestProperty\(|addRequestProperty\(" tmp_jadx/sources/com/librelink/app 2>/dev/null | head -n 300
```

```bash
grep -RniE "newYuUrl|/lsl|Libreview|FSLibreLink\.Android|activeSensor|CurrentGlucoseReceivedEvent|ReceivedEvent" tmp_jadx/sources 2>/dev/null | head -n 300
```

```bash
grep -RniE "clearDatabase\(|getCurrentlySelectedSensor\(|activeSensorToUpload|domainData|after login|Removing previous user data" tmp_jadx/sources 2>/dev/null | head -n 300
```

## 6) Comprobacion de recursos (configuracion por pais y web bridge)

Listado de recursos de texto para localizar configuraciones:

```bash
find tmp_jadx/resources -type f | grep -E 'json$|xml$|txt$' | head -n 200
```

Hallazgos clave en recursos:

- Config por pais con capa NewYu:
  - tmp_jadx/resources/assets/config/GB_config.json
- Claves encontradas:
  - newYuUrl (host adc-lv.io/lsl)
  - newYuDomain
  - newYuGateway
  - newYuShareUrl con ruta sharing/legacytoken

## 7) Comprobacion en binarios/librerias nativas

Para descartar endpoints ocultos fuera del Java decompilado:

```bash
find tmp_apkm_inspect -type f \( -name '*.so' -o -name 'base.apk' -o -name '*.dex' \) | head -n 200
```

```bash
grep -aRniE "https://|http://|newYu|measurements|libreview|freestyleserver|oneStep|graphql|apollo|glucose|sensor|activeSensor" tmp_apkm_inspect 2>/dev/null | head -n 300
```

## 8) Comprobaciones manuales de codigo decompilado

Ademas de grep, se leyeron archivos concretos para validar significado funcional:

- API y llamadas de red visibles:
  - tmp_jadx/sources/com/librelink/app/network/NumeraWebApi.java
- Flujo de login y uso de domainData / activeSensor:
  - tmp_jadx/sources/com/librelink/app/network/b.java
- Subida de sensor activo y jobs de upload:
  - tmp_jadx/sources/com/librelink/app/jobs/ActiveSensorUploadJob.java
  - tmp_jadx/sources/com/librelink/app/jobs/DataUploadJob.java
  - tmp_jadx/sources/defpackage/C3502p3.java
- Comportamiento local/DB (SAS) tras limpieza de datos:
  - tmp_jadx/sources/defpackage/II0.java
- WebView y puente legacy token:
  - tmp_jadx/sources/com/librelink/app/ui/common/WebViewActivity.java

## 9) Resultado tecnico de la investigacion

Con la evidencia obtenida:

- Si aparece una capa NewYu (adc-lv.io/lsl) y rutas orientadas a autenticacion/subida.
- Si aparece bridge web legacy (sharing/legacytoken) para abrir contenido autenticado en WebView.
- No aparecio un endpoint claro y directo de descarga de historico (90 dias) en las interfaces visibles de red decompiladas.
- La evidencia visible apunta mas a:
  - Upload de mediciones/sensor
  - Restauracion de contexto (activeSensor)
  - Uso local de datos para estadisticas

## 10) Conclusiones y limites

Conclusiones:

- No se pudo demostrar un endpoint explicito de descarga historica con decompilacion estatica.
- Si existe, probablemente esta ofuscado/no trazable en capa no visible, o en un flujo runtime no evidente.

Limites:

- Decompilacion estatica no garantiza visibilidad completa de todos los flujos runtime.
- Parte del comportamiento puede depender de configuracion remota, feature flags o flujos web.

## 11) Siguiente paso recomendado para confirmacion 100%

Captura de trafico real tras borrar datos e iniciar sesion (mitmproxy/Charles), filtrando por:

- hosts adc-lv.io / libreview
- rutas con measurements, graph, history, report, insight
- llamadas justo tras login y restauracion de sesion

Ese paso permite confirmar de forma definitiva si existe o no endpoint de descarga historica en ejecucion real.

## 12) Limpieza opcional de artefactos temporales

```bash
rm -rf tmp_apkm_inspect tmp_jadx
```
