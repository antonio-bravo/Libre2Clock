# Guía: Arreglar error 'closed' en el Emulador

Si el script `./fix_emulator.sh` y el **"Cold Boot Now"** no han funcionado, el emulador tiene un problema de datos corruptos en su disco virtual. Sigue esta escala de soluciones:

## Nivel 1: Limpieza Total (Wipe Data)
Esto borrará los datos de las aplicaciones instaladas en el emulador (lo dejará como de fábrica) pero conservará el emulador.

1.  Cierra la ventana del emulador.
2.  Abre el **Device Manager** en Android Studio.
3.  Busca `emulator-5554` (Medium Phone API 36).
4.  Haz clic en los tres puntos **(⋮)**.
5.  Selecciona **"Wipe Data"**.
6.  Confirma y luego inicia el emulador normalmente.

## Nivel 2: Reinstalar Servidor ADB
A veces hay un conflicto de procesos en tu Mac:

1.  Ejecuta en la terminal:
    ```bash
    killall adb
    adb start-server
    ```

## Nivel 3: El "Botón Nuclear" (Nuevo Emulador)
Si nada de lo anterior funciona, la imagen del emulador está dañada.

1.  En el **Device Manager**, borra el emulador actual (icono de papelera).
2.  Pulsa **"Create virtual device"**.
3.  Elige un modelo (ej. Pixel 8) y una versión de Android (te recomiendo **API 35** o **34** si la 36 te da problemas, ya que son más estables).
4.  Finaliza la creación y lánzalo.

---

### ¿Por qué sigue fallando?
El error `'closed'` significa que el emulador "ve" la conexión pero el proceso interno que gestiona las apps (`adbd`) se muere al instante. Esto suele ser por:
- Falta de espacio en el disco interno del emulador.
- Corrupción de la partición `/data` del emulador.
- Inestabilidad de la versión API 36 (Android 15+).
