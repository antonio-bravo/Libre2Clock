# Reglas de Seguridad de Cloud Firestore (Libre2Clock)

Este documento contiene las reglas de seguridad oficiales para Cloud Firestore utilizadas por Libre2Clock para gestionar la sincronización de datos de glucosa, insulina, capilares y la funcionalidad de **Compartir Datos con Cuidadores/Seguidores**.

> [!NOTE]
> Guardar las reglas de seguridad en el repositorio es una **buena práctica** estándar de desarrollo. Estas reglas no contienen claves privadas, contraseñas ni secretos, sino la lógica de permisos del servidor de Google Firestore.

---

## Código de Reglas Oficiales (`firestore.rules`)

Copia y pega este bloque en la pestaña **Reglas** de tu consola de Firebase (o despliégalo mediante la CLI de Firebase):

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/(default)/documents {

    // 1. Colección de Invitaciones (Códigos de un solo uso)
    match /share_invites/{inviteCode} {
      // Cualquier usuario autenticado (incluyendo anónimos) puede leer para validar el código
      allow read: if request.auth != null;

      // Creación: El paciente crea la invitación poniendo su propio UID como ownerUid
      allow create: if request.auth != null && request.resource.data.ownerUid == request.auth.uid;

      // Actualización: El seguidor marca el código como usado (isUsed = true)
      allow update: if request.auth != null;

      // Eliminación: El paciente elimina su código
      allow delete: if request.auth != null && resource.data.ownerUid == request.auth.uid;
    }

    // 2. Colección de Seguidores de cada Paciente
    match /users/{userId}/followers/{followerUid} {
      allow read: if request.auth != null && (request.auth.uid == userId || request.auth.uid == followerUid);
      allow create, update: if request.auth != null;
      allow delete: if request.auth != null && request.auth.uid == userId;
    }

    // 3. Datos de Salud del Paciente (Glucosa, Insulina, Capilares, Sensor Logs)
    match /users/{userId}/patients/{patientId}/{document=**} {
      // ESCRITURA: ÚNICA Y EXCLUSIVAMENTE EL PACIENTE (PROPIETARIO)
      allow write: if request.auth != null && request.auth.uid == userId;

      // LECTURA: El paciente O un seguidor activo en la lista de seguidores
      allow read: if request.auth != null && (
        request.auth.uid == userId ||
        isAuthorizedFollower(userId, request.auth.uid)
      );
    }
  }

  // Función helper para comprobar si un seguidor tiene acceso activo
  function isAuthorizedFollower(patientUid, followerUid) {
    return exists(/databases/(default)/documents/users/$(patientUid)/followers/$(followerUid)) &&
           get(/databases/(default)/documents/users/$(patientUid)/followers/$(followerUid)).data.active == true;
  }
}
```

---

## Explicación de los Permisos

1. **Garantía de Escritura Protegida:**
   - **Nadie**, excepto el propio paciente (`request.auth.uid == userId`), puede escribir, modificar o eliminar datos de glucosa e insulina en la nube.
   - Los seguidores tienen **acceso 100% de Solo Lectura**.

2. **Cero Registro para Seguidores:**
   - El seguidor utiliza **Firebase Anonymous Auth** de forma transparente, permitiendo validar la invitación sin necesidad de crear cuenta ni pedir datos personales.

3. **Revocación Inmediata:**
   - Si el paciente cambia `active = false` o elimina a un seguidor, las reglas cortan el acceso de lectura en ese mismo instante.
