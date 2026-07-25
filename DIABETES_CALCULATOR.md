# Calculadora de Diabetes (Bolus Helper)

Libre2Clock incluye una calculadora avanzada para ayudar a determinar la dosis de insulina necesaria basada en carbohidratos y glucosa actual.

## 1. Ratios y Sensibilidad

La calculadora se basa en la **Dosis Diaria Total (TDI)**, que la app calcula automáticamente como la media de los últimos 30 días, o que el usuario puede fijar manualmente.

### Ratio Insulina:Carbohidratos (I:C)
Utiliza la **Regla del 450** (configurable):
- **Fórmula**: `Ratio = 450 / TDI`
- **Significado**: Cuántos gramos de carbohidratos cubre 1 unidad de insulina.

### Factor de Sensibilidad a la Insulina (ISF)
Utiliza la **Regla del 1800** (configurable):
- **Fórmula**: `ISF = 1800 / TDI`
- **Significado**: Cuántos mg/dL baja la glucosa con 1 unidad de insulina.

---

## 2. Cálculo del Bolo Sugerido

La calculadora devuelve dos valores simultáneos: **Bolo Real** (basado en el sensor) y **Bolo Offset** (basado en el valor calibrado).

### Componentes del cálculo:
1.  **Bolo por Comida**: `Carbohidratos / Ratio I:C`
2.  **Bolo de Corrección**: `(Glucosa_Actual - Glucosa_Objetivo) / ISF`
    - *Nota: El objetivo por defecto es **80 mg/dL**.*

**Fórmula Final**: `Sugerencia = Bolo_Comida + Bolo_Corrección`

---

## 3. Ajustes Especiales

### Aviso de Basal Próxima a Terminar
Si el sistema detecta que tu última dosis de insulina lenta/basal va a expirar en menos de **2 horas**:
- Se muestra un aviso de advertencia en rojo.
- La calculadora aplica automáticamente un **incremento del 20%** a la dosis sugerida para compensar la falta de basal y evitar una subida posterior.

### Prioridad Manual
Si el usuario introduce un **ISF Manual** o un **TDI Manual** en los ajustes, la calculadora ignorará los promedios automáticos y usará exclusivamente los valores proporcionados por el usuario.
