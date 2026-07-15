# How To Fix: Notificacion llega al telefono pero no al reloj

## Sintoma
- Libre2Clock envia la notificacion y se ve en el telefono.
- El reloj (Bip S con Zepp Life) no la recibe.

## Causa encontrada
En Zepp Life, aunque las notificaciones parecian activadas, el estado interno quedo inconsistente.

## Solucion rapida (confirmada)
1. Abrir Zepp Life.
2. Ir a Notificaciones de aplicaciones.
3. Desactivar todas las notificaciones de apps (o al menos Libre2Clock).
4. Cerrar Zepp Life por completo.
5. Volver a abrir Zepp Life.
6. Activar otra vez las notificaciones de apps.
7. Asegurar que Libre2Clock este marcada como app permitida.
8. Hacer un test desde Libre2Clock (Settings -> Test Notification).

## Si aun no llega al reloj
1. Revisar permiso de notificaciones de Zepp Life en Android.
2. Revisar permiso de acceso a notificaciones para Zepp Life (Notification Access).
3. Quitar optimizacion de bateria para Zepp Life y Libre2Clock.
4. Confirmar Bluetooth activo y reloj sincronizado.
5. Reiniciar Bluetooth (apagar/encender) y volver a sincronizar en Zepp Life.
6. Probar con otra app (ej. WhatsApp):
- Si no llega ninguna app: problema Zepp/Bluetooth/permisos globales.
- Si otras apps llegan y Libre2Clock no: revisar filtro por app en Zepp Life y canal de notificacion en Android.

## Checklist despues de reiniciar el telefono
1. Abrir Zepp Life al menos una vez.
2. Verificar que el reloj aparece conectado.
3. Confirmar que Libre2Clock sigue habilitada en Notificaciones de apps de Zepp Life.
4. En Libre2Clock, ejecutar Test Notification.

## Nota tecnica del proyecto
Libre2Clock genera la notificacion correctamente en el telefono. Si no llega al reloj, normalmente el fallo esta en el puente Android -> Zepp Life -> reloj.
