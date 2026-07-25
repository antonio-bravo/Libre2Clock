# Algoritmo de Cálculo de Insulina Activa (IOB)

Este documento detalla cómo Libre2Clock calcula la insulina que aún permanece activa en el cuerpo (Insulin On Board - IOB).

## 1. Insulina Rápida (Algoritmo Personalizado)

Para la insulina rápida, el sistema utiliza un modelo de **interpolación por tramos** dividido en 4 fases de consumo durante un periodo base de 4 horas (240 minutos).

### Fórmula General
$$ \text{IOB} = \text{Dosis Inicial} \times \text{Factor Restante} $$
*(El resultado final se redondea al entero más cercano)*

### Tramos de Cálculo

| Minutos (t) | Lógica de Consumo / Factor Restante | Descripción |
| :--- | :--- | :--- |
| **0 - 59** | $1.0 - (0.0033 \times t)$ | Consumo inicial constante (0.33% por minuto). |
| **60 - 179** | $0.802 - (0.53885 \times \text{progreso})$ | Descenso hacia el 26.32% a las 3 horas. |
| **180 - 184** | $0.26315 - (0.013155 \times (t - 180))$ | Ventana crítica de caída de precisión. |
| **185 - 239** | $0.21053 \times (1.0 - \text{progreso final})$ | Cola final hasta llegar a cero. |
| **240+** | $0$ | Insulina totalmente consumida. |

---

## 2. Insulina Lenta / Basal (Modelo Lineal)

Para la insulina lenta (con duración típica de 24 horas), el sistema utiliza un modelo de **decaimiento lineal**. Esto refleja una liberación constante y estable del medicamento a lo largo del tiempo.

### Fórmula
$$ \text{IOB} = \text{Dosis} \times \left(1 - \frac{\text{Minutos Transcurridos}}{\text{Duración Total Configurada}}\right) $$

---

## 3. Ejemplo Práctico: Dosis de 19.0 U (Rápida)

A continuación se muestra cómo evoluciona una dosis de 19 unidades de insulina rápida según el algoritmo:

1.  **A los 0 minutos**: 100% activo → **19 U**.
2.  **A los 60 minutos (1h)**: Factor 0.802 → $19 \times 0.802 = 15.23$ → **15 U** (redondeado).
3.  **A los 180 minutos (3h)**: Factor 0.26315 → $19 \times 0.26315 = 5.00$ → **5 U**.
4.  **A los 184 minutos**: Factor 0.21053 → $19 \times 0.21053 = 4.00$ → **4 U**.
5.  **A los 240 minutos (4h)**: Factor 0.0 → **0 U**.

---

## 4. Notas Técnicas
- **Redondeo**: El motor de la aplicación aplica `roundToInt()` a cada dosis individual antes de sumarlas al total de IOB mostrado en el Dashboard.
- **Configuración**: Si el usuario cambia la duración de la insulina rápida en los ajustes (ej. a 5 horas), el algoritmo escala los tramos proporcionalmente para mantener la forma de la curva de decaimiento.
