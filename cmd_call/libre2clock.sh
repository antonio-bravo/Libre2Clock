# 1) Login
BASE="https://api.libreview.io"
EMAIL="email@example.com"
PASS="password"

LOGIN=$(curl -sS -X POST "$BASE/llu/auth/login" \
  -H "product: llu.android" \
  -H "version: 4.16.0" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"$PASS\"}")

echo "$LOGIN" | jq '.status, .data.user.id, .data.authTicket.token'
TOKEN=$(echo "$LOGIN" | jq -r '.data.authTicket.token')
USER_ID=$(echo "$LOGIN" | jq -r '.data.user.id')

# 2) Account-Id en SHA-256 (igual que la app)
ACCOUNT_ID=$(printf "%s" "$USER_ID" | shasum -a 256 | awk '{print $1}')

# 3) Connections -> patientId
CONN=$(curl -sS "$BASE/llu/connections" \
  -H "product: llu.android" \
  -H "version: 4.16.0" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Account-Id: $ACCOUNT_ID")

PATIENT_ID=$(echo "$CONN" | jq -r '.data[0].patientId')
echo "PATIENT_ID=$PATIENT_ID"

# 4) Graph
GRAPH=$(curl -sS "$BASE/llu/connections/$PATIENT_ID/graph" \
  -H "product: llu.android" \
  -H "version: 4.16.0" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Account-Id: $ACCOUNT_ID")

# 5) Diagnóstico rápido de ventana temporal
echo "$GRAPH" | jq '.status'
echo "$GRAPH" | jq '.data.graphData | length'
echo "$GRAPH" | jq -r '.data.graphData | map(.FactoryTimestamp) | min, max'