#!/bin/bash

# --- CONFIGURACIÓN ---
BASE="https://api.libreview.io" # Base inicial global
EMAIL="email@example.com"
PASS="password"

echo "1) Iniciando sesión..."

# Guardamos el JSON de login directamente en un archivo temporal
curl -sS -X POST "$BASE/llu/auth/login" \
  -H "product: llu.android" \
  -H "version: 4.16.0" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}" > login_response.json

# Validamos si el archivo se creó correctamente y extraemos datos
if [ ! -s login_response.json ]; then
    echo "Error: No se recibió respuesta del servidor."
    exit 1
fi

# Extraemos credenciales básicas
TOKEN=$(jq -r '.data.authTicket.token // empty' login_response.json)
USER_ID=$(jq -r '.data.user.id // empty' login_response.json)

# MEJORA: Comprobar si requiere redirección regional (Ej: api-eu.libreview.io)
REGION_REDIRECT=$(jq -r '.data.redirect // empty' login_response.json)
if [ "$REGION_REDIRECT" = "true" ]; then
    REGION=$(jq -r '.data.region // empty' login_response.json)
    BASE="https://api-$REGION.libreview.io"
    echo "-> Redirigiendo a servidor regional: $BASE"
fi

if [ -z "$TOKEN" ]; then
    echo "Error en la autenticación. Revisa 'login_response.json' para ver el motivo."
    exit 1
fi

echo "✓ Sesión iniciada. Token guardado."

# 2) Generar Account-Id en SHA-256 (Requerido por la App)
ACCOUNT_ID=$(printf "%s" "$USER_ID" | shasum -a 256 | awk '{print $1}')

echo "3) Obteniendo lista de conexiones..."

# Guardamos el JSON de conexiones en un archivo físico
curl -sS "$BASE/llu/connections" \
  -H "product: llu.android" \
  -H "version: 4.16.0" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Account-Id: $ACCOUNT_ID" > connections_response.json

# Extraemos el primer ID de paciente disponible
PATIENT_ID=$(jq -r '.data[0].patientId // empty' connections_response.json)

if [ -z "$PATIENT_ID" ] || [ "$PATIENT_ID" = "null" ]; then
    echo "Error: No se encontraron pacientes vinculados. Revisa 'connections_response.json'."
    exit 1
fi

echo "✓ Patient_ID encontrado: $PATIENT_ID"

echo "4) Obteniendo gráfica de glucosa..."

# Guardamos el JSON de las mediciones en un archivo físico
curl -sS "$BASE/llu/connections/$PATIENT_ID/graph" \
  -H "product: llu.android" \
  -H "version: 4.16.0" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Account-Id: $ACCOUNT_ID" > graph_response.json

echo "✓ Datos de la gráfica descargados."

# 5) Diagnóstico rápido en pantalla leyendo los archivos guardados
echo "----------------------------------------"
echo "ESTADÍSTICAS DEL ANÁLISIS:"
echo "Estado de la gráfica: $(jq '.status' graph_response.json)"
echo "Total mediciones (longitud): $(jq '.data.graphData | length' graph_response.json)"
echo "Rango de tiempo (Min, Max UTC):"
jq -r '.data.graphData | map(.FactoryTimestamp) | min, max' graph_response.json
echo "----------------------------------------"
echo "Archivos listos para analizar en tu carpeta:"
echo " - login_response.json"
echo " - connections_response.json"
echo " - graph_response.json"
