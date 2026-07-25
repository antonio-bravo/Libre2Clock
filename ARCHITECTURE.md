# Arquitectura del Proyecto

Libre2Clock está construida siguiendo los principios modernos de desarrollo Android: **Clean Architecture** (simplificada) y **MVVM** (Model-View-ViewModel), utilizando **Jetpack Compose** para una UI declarativa.

## 1. Flujo de Datos (Unidireccional)

1.  **Fuente de Datos**: `LibreService` conecta con los endpoints de LibreLinkUp.
2.  **Repositorio**: `GlucoseRepositoryImpl` gestiona la sincronización. Decide si usar datos reales o el modo Demo.
3.  **Persistencia**: `PreferenceManager` utiliza **DataStore** para guardar tokens, historial de insulina y configuraciones de calibración.
4.  **Segundo Plano**: `GlucoseForegroundService` mantiene la sincronización activa cada minuto y gestiona las notificaciones al reloj/móvil.
5.  **UI**: Los **ViewModels** observan los flujos de datos (`Flow`) de los repositorios y exponen el estado a las pantallas de **Compose**.

## 2. Tecnologías Clave

- **Jetpack Compose**: Interfaz de usuario 100% Kotlin.
- **Coroutines & Flow**: Manejo asíncrono y reactivo de los datos.
- **Retrofit & Moshi**: Comunicación con la API y parseo de JSON.
- **Navigation 3**: Sistema de navegación moderno para pantallas y diálogos.

## 3. Estructura de Carpetas

- `data/api/`: Definiciones de Retrofit para LibreLinkUp.
- `data/model/`: Modelos de datos (Insulina, Glucosa, Sensores).
- `data/repository/`: Lógica de negocio (Cálculo de IOB, Offsets, Procesamiento de Glucosa).
- `service/`: El servicio de Android que corre en primer plano.
- `ui/`: Pantallas organizadas por funcionalidad (dashboard, insulin, settings).
- `util/`: Utilidades comunes (parseo de fechas, etc.).

## 4. Gestión de Estado
Se utiliza `collectAsStateWithLifecycle()` en Compose para asegurar que la UI solo se actualice cuando es necesario y no consuma recursos si la app está en segundo plano.
