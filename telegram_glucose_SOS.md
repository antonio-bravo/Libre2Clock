Aquí tienes una guía sencilla paso a paso para crear tu propio Bot de Telegram en menos de 3 minutos y obtener los datos necesarios para configurarlo en Libre2Clock:

---

### 📌 Paso 1: Crear tu Bot de Telegram (Obtener el Token)

1. Abre la aplicación de **Telegram** en tu teléfono o PC.
2. En el buscador de Telegram, busca el usuario oficial **`@BotFather`** *(tiene un tic azul de verificación)*.
3. Entra al chat de BotFather y pulsa **Iniciar** o escribe el comando `/newbot`.
4. BotFather te pedirá dos nombres:
   * **Nombre para tu Bot**: Pon lo que quieras (ejemplo: *Mi Alerta SOS Glucosa*).
   * **Nombre de usuario para tu Bot**: Debe terminar obligatoriamente en `bot` (ejemplo: *MiGlucosaSOS_bot*).
5. BotFather te responderá con un mensaje felicitándote y te entregará una clave llamada **HTTP API Token**.
   * Tiene un formato como este: `7123456789:AAFxAbcDeFgHiJkLmNoPqRsTuVwXyZ1234`
6. **Copia ese Token** y pégalo en Libre2Clock dentro de **Ajustes ➔ Contactos y Alertas SOS ➔ Token de Bot de Telegram**.

---

### 📌 Paso 2: Obtener el Chat ID de tus contactos o de un grupo

> ⚠️ **Nota de seguridad de Telegram**: Por privacidad, un bot no puede enviar mensajes a alguien de la nada. **Tu familiar o grupo debe abrir el bot y pulsar "Iniciar" al menos una vez**.

#### **Opción A: Enviar a un contacto individual**
1. Dile a tu familiar/contacto que busque el nombre de tu bot en Telegram (ej: `@MiGlucosaSOS_bot`) y pulse **Iniciar**.
2. Para averiguar el número **Chat ID** de ese familiar:
   * Abre Telegram y busca el bot **`@userinfobot`**.
   * Haz que tu familiar le mande cualquier mensaje a `@userinfobot`.
   * El bot le responderá con su número de **Id** (ejemplo: `123456789`).
3. En Libre2Clock, edita tu contacto de emergencia y pega ese número (`123456789`) en el campo **Chat ID de Telegram**.

#### **Opción B: Enviar a un Grupo Familiar (⭐ Opción Recomendada)**
¡Esta opción es la mejor porque avisa a toda tu familia al mismo tiempo!
1. Crea un grupo en Telegram (ej: *"Alertas Glucosa Familia"*).
2. Añade a tus familiares y añade también a tu bot (ej: `@MiGlucosaSOS_bot`) al grupo.
3. Para obtener el **Chat ID del grupo**:
   * Añade temporalmente al bot **`@myidbot`** al grupo.
   * Escribe en el grupo el comando `/getgroupid`.
   * El bot te dará un número que empieza por signo menos (ejemplo: `-1001234567890`).
   * Ya puedes eliminar a `@myidbot` del grupo.
4. En Libre2Clock, crea un contacto llamado "Grupo Familia", pon como Chat ID de Telegram el número completo incluyendo el signo menos (`-1001234567890`) y marca la casilla **Telegram Bot**.

---

### 📌 Paso 3: Probar la Alerta

1. Abre Libre2Clock e ve a **Ajustes ➔ Contactos y Alertas SOS**.
2. Asegúrate de tener el **Token del Bot** y al menos un contacto con su **Chat ID** guardado.
3. Pulsa el botón **"Probar Alerta SOS Ahora"**.
4. En cuestión de segundos, la alerta de prueba con tu nivel de glucosa, fecha, hora y enlace GPS en Google Maps llegará de forma automática al chat o grupo de Telegram.
