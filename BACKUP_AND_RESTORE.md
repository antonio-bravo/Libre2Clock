# Respaldo y Restauración de Datos

Libre2Clock incluye herramientas para asegurar que no pierdas tu historial de insulina ni tus calibraciones personales.

## 1. Google Cloud Backup (Automático)
La aplicación está integrada con el servicio de Backup de Android.
- **Qué se guarda**: Tus ajustes (offsets, duraciones, reglas), historial de insulina y mediciones capilares.
- **Frecuencia**: Se solicita un respaldo cada 24 horas automáticamente o al realizar cambios importantes.
- **Restauración**: Al instalar la app en un teléfono nuevo con la misma cuenta de Google, los datos se recuperan solos.

## 2. Exportación JSON Local (Manual)
Puedes crear una "foto" de tus datos en cualquier momento desde los ajustes.
- **Exportar**: Genera un archivo `.json` en tu carpeta de Descargas (`Downloads/Libre2Clock/`).
- **Importar**: Permite seleccionar un archivo `.json` previo para restaurar el historial. Los datos se fusionan de forma inteligente (no se duplican registros que ya existen).

## 3. Privacidad
- El archivo de backup contiene tus datos de salud localmente. 
- Te recomendamos no compartir el archivo JSON generado con terceros, ya que incluye tu historial detallado.
