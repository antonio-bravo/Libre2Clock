# 🤖 Bot Webhook de WhatsApp para Libre2Clock

Servidor privado en Node.js que recibe en segundo plano las alertas SOS de hipoglucemia desde la aplicación **Libre2Clock** y las publica automáticamente en tu **Grupo de WhatsApp** sin intervención del usuario ni toques de pantalla.

---

## 🛠️ Requisitos Previos
1. Una cuenta gratuita en **[GitHub](https://github.com)** con este repositorio subido.
2. Una cuenta gratuita en **[Render.com](https://render.com)**.
3. Una cuenta de WhatsApp (personal o con un número secundario).

---

## 🚀 Guía de Despliegue Paso a Paso en Render.com

1. Entra en tu panel de **[Render.com](https://dashboard.render.com)**.
2. Pulsa el botón azul **New +** (arriba a la derecha) y selecciona **Web Service**.
3. Elige la opción **Build and deploy from a Git repository** y pulsa *Next*.
4. Conecta tu cuenta de GitHub y selecciona tu repositorio `Libre2Clock`.
5. Rellena los campos del formulario con la siguiente configuración exacta:

| Campo | Valor a Introducir |
| :--- | :--- |
| **Name** | `libre2clock-whatsapp-bot` |
| **Region** | `Frankfurt (EU)` *(o la más cercana)* |
| **Branch** | `main` *(o `master`)* |
| **Root Directory** | `whatsapp-bot` ⚠️ *(¡Esencial! Indica la subcarpeta)* |
| **Runtime** | `Node` |
| **Build Command** | `npm install` |
| **Start Command** | `npm start` |
| **Instance Type** | `Free` ($0 / mes) |

6. Haz clic en el botón azul **Create Web Service**.

---

## 📱 Vinculación con WhatsApp (Paso Único)

1. Una vez creado el servicio, ve a la pestaña **Logs** en el panel de Render..
2. Verás aparecer un **código QR** dibujado en los logs del servidor.
3. En tu teléfono móvil, abre **WhatsApp**:
   * Toca en los tres puntos (o Ajustes) ➔ **Dispositivos vinculados**.
   * Selecciona **Vincular un dispositivo** y escanea el código QR de la pantalla de Render.
4. En los logs de Render aparecerá el mensaje:  
   `✅ Bot de WhatsApp listo, conectado y en línea!`.

---

## 👥 Obtener el ID del Grupo de WhatsApp

1. Añade la cuenta de WhatsApp que acabas de vincular a tu **Grupo de WhatsApp** de emergencias (ejemplo: *"Familia Alertas Glucosa"*).
2. Dentro del grupo de WhatsApp, escribe el comando:
   ```text
   /id
   ```
3. El bot te responderá inmediatamente en el grupo con su ID único:
   ```text
   📍 ID de este Grupo de WhatsApp:
   120363012345678901@g.us
   ```
4. Copia ese ID (incluyendo el `@g.us`).

---

## ⚙️ Configuración en la App Libre2Clock

1. Abre **Libre2Clock** en tu móvil Android.
2. Ve a **Ajustes** ➔ **Contactos y Alertas SOS**.
3. En el campo **URL de Webhook Personalizado**, pega la URL pública que te asignó Render.com añadiendo `/sos-webhook`:
   ```text
   https://libre2clock-whatsapp-bot.onrender.com/sos-webhook
   ```
4. Edita o añade tu contacto de emergencia en la lista:
   * **Nombre:** *Grupo Familiar WhatsApp*
   * **ID de Grupo de WhatsApp:** Pegas el ID copiado previamente (`120363012345678901@g.us`).
   * **Canales:** Marca la casilla **WhatsApp**.
5. Pulsa **Guardar**.
6. Haz clic en **Probar Alerta SOS Completa** para verificar que el mensaje llega al instante a tu grupo de WhatsApp.

---

## 🔒 Seguridad y Privacidad

* Tu servidor en Render es **100% privado e independiente**.
* Toda la configuración de IDs de grupo reside únicamente dentro de tu app Libre2Clock en tu teléfono.
* El bot no almacena datos de salud ni historiales de glucosa.
