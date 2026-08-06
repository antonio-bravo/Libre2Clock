# Sistema de Calibración de Glucosa

Libre2Clock permite ajustar los valores recibidos de LibreLinkUp para corregir desviaciones habituales entre el líquido intersticial y la sangre capilar.

## 1. Offset Manual Global
Es un valor fijo que se suma o resta a **todas** las mediciones del sensor.
- **Ejemplo**: Si tienes un offset de +20 y el sensor marca 100, la app mostrará `100(120)`.

## 2. Rangos de Offset (Range-Based)
Permite definir correcciones distintas según el nivel de glucosa. Esto es útil porque los sensores suelen ser más precisos en rangos normales que en hipos o hipers.
- **Lógica**: La app busca en qué rango cae la lectura actual y aplica el offset definido para ese tramo.
- **Ejemplo**:
  - 0 a 70: +10
  - 70 a 150: +20
  - 150 a 300: +30

## 3. Sistema de Auditoría de Sensor (Prueba de Error)
Para cada rango definido, la aplicación realiza un análisis estadístico automático comparando tus mediciones capilares con lo que marcó el sensor de fábrica.
- **Error Sensor (Sesgo/Bias)**: Calcula el porcentaje técnico de error del hardware. Un valor de -15% indica que el sensor mide sistemáticamente un 15% por debajo de la realidad.
- **Comparativa de Medias**: Muestra el valor promedio del sensor frente al promedio capilar en ese rango específico.
- **Validación**: Indica cuántas pruebas respaldan este cálculo para dar validez técnica a tus reclamaciones o ajustes.

## 4. Auto-Ajuste Capilar (Algoritmo Inteligente)
Si se activa, la aplicación utiliza tus últimas mediciones capilares guardadas para calcular una desviación media.
- **Funcionamiento**: La app compara tus lecturas capilares con lo que marcaba el sensor en ese mismo minuto.
- **Aplicación**: Calcula el porcentaje de error medio y lo aplica a la lectura actual para predecir un valor más cercano a la realidad capilar.

## Visualización Dual
En toda la interfaz verás el formato: **`Valor_Real(Valor_Corregido)`**.
- **Valor_Real**: El dato bruto descargado de los servidores de Libre.
- **Valor_Corregido**: El resultado de aplicar tus reglas de offset.

> [!TIP]
> Las alarmas de glucosa pueden configurarse para saltar basadas en el valor corregido, lo que evita falsas alarmas de hipoglucemia si sabes que tu sensor suele marcar de menos.
