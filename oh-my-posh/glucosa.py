#!/opt/anaconda3/bin/python
import hashlib
import os
import shutil
import subprocess
import sys
import time
import requests

# --- CONFIGURACIÓN ---
EMAIL = os.getenv("LIBRELINK_EMAIL", "").strip()
PASSWORD = os.getenv("LIBRELINK_PASSWORD", "").strip()
PATIENT_NAME = os.getenv("LIBRELINK_PATIENT", "").strip()
REGIONAL_URL = os.getenv("LIBRELINK_REGION", "https://api-eu.libreview.io")
CACHE_FILE = os.path.expanduser("~/.glucosa_cache.txt")
CACHE_DURATION = 300  # 5 minutos en segundos
USE_CACHE = os.getenv("GLUCOSA_USE_CACHE", "0").lower() in {"1", "true", "yes", "on"}
NOTIFY_ENABLED = os.getenv("GLUCOSA_NOTIFY_ENABLED", "0").lower() in {"1", "true", "yes", "on"}
NOTIFY_INTERVAL_SECONDS = int(os.getenv("GLUCOSA_NOTIFY_INTERVAL_SECONDS", "0") or "0")
NOTIFY_INTERVAL_MINUTES = int(os.getenv("GLUCOSA_NOTIFY_INTERVAL_MINUTES", "0") or "0")
NOTIFY_INTERVAL_HOURS = int(os.getenv("GLUCOSA_NOTIFY_INTERVAL_HOURS", "0") or "0")
NOTIFY_INTERVAL_SECONDS = (
    NOTIFY_INTERVAL_SECONDS
    + NOTIFY_INTERVAL_MINUTES * 60
    + NOTIFY_INTERVAL_HOURS * 3600
)
if NOTIFY_ENABLED and NOTIFY_INTERVAL_SECONDS <= 0:
    NOTIFY_INTERVAL_SECONDS = 300

# Tabla de rangos para aplicar un offset al valor mostrado.
# Se puede ajustar fácilmente según el rango en el que se encuentre la medida.
OFFSET_RANGES = [
    (0, 70, 20),
    (70, 100, 40),
    (100, 140, 60),
    (140, 200, 80),
    (200, None, 80),
]


def formatear_valor(valor):
    try:
        numero = float(valor)
    except (TypeError, ValueError):
        return str(valor)

    if numero.is_integer():
        return str(int(numero))
    return str(round(numero, 1))


def calcular_valor_ajustado(valor):
    try:
        numero = float(valor)
    except (TypeError, ValueError):
        return None

    for lower, upper, offset in OFFSET_RANGES:
        if (lower is None or numero >= lower) and (upper is None or numero <= upper):
            return round(numero + offset, 1)

    return round(numero, 1)


def build_headers(token=None, account_id=None):
    headers = {
        "accept-encoding": "gzip",
        "cache-control": "no-cache, no-store, must-revalidate",
        "pragma": "no-cache",
        "expires": "0",
        "connection": "Keep-Alive",
        "content-type": "application/json",
        "accept": "application/json",
        "product": "llu.android",
        "version": "5.0.1",
        "user-agent": "LibreLinkUp/5.0.1",
    }
    if token:
        headers["authorization"] = f"Bearer {token}"
        headers["Authorization"] = f"Bearer {token}"
    if account_id:
        account_id_hash = hashlib.sha256(str(account_id).encode("utf-8")).hexdigest()
        headers["account-id"] = account_id_hash
        headers["Account-Id"] = account_id_hash
    return headers


def send_notification(message):
    if not NOTIFY_ENABLED:
        return

    title = "Glucosa"
    escaped = message.replace("\\", "\\\\").replace('"', '\\"')

    if shutil.which("osascript"):
        payload = f'display notification "{escaped}" with title "{title}"'
        subprocess.run(["osascript", "-e", payload], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)
    elif shutil.which("terminal-notifier"):
        subprocess.run(["terminal-notifier", "-title", title, "-message", message], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


def obtener_desde_servidor():
    versions = ["5.0.1", "4.20.0", "4.18.0", "4.16.0", "4.16.1", "4.16.2"]
    base_urls = []
    configured_url = REGIONAL_URL.strip()
    if configured_url:
        base_urls.append(configured_url)
    for candidate in ["https://api-eu.libreview.io", "https://api.libreview.io"]:
        if candidate not in base_urls:
            base_urls.append(candidate)

    last_error = None
    for base_url in base_urls:
        try:
            # 1. Autenticación
            login_url = f"{base_url}/llu/auth/login"
            for version in versions:
                headers = build_headers()
                headers["version"] = version
                response = requests.post(
                    login_url,
                    json={"email": EMAIL, "password": PASSWORD},
                    headers=headers,
                    timeout=15,
                )
                if response.status_code == 200:
                    break
                if response.status_code != 403:
                    last_error = f"ERR_HTTP_LOGIN {response.status_code}: {response.text[:120]}"
                    break

            if response.status_code != 200:
                last_error = f"ERR_HTTP_LOGIN {response.status_code}: {response.text[:120]}"
                continue

            data = response.json()
            if data.get("status") != 0:
                message = data.get("message") or data.get("error") or "respuesta inesperada"
                last_error = f"ERR_AUTH: {message}"
                continue

            token = data.get("data", {}).get("authTicket", {}).get("token")
            if not token:
                last_error = "ERR_TOKEN"
                continue

            account_id = None
            user_data = data.get("data", {}).get("user") or data.get("user") or {}
            if isinstance(user_data, dict):
                account_id = user_data.get("id") or user_data.get("userId") or user_data.get("accountId")
            if not account_id:
                account_id = data.get("data", {}).get("accountId") or data.get("accountId")

            headers = build_headers(token, account_id)

            # 2. Conexiones
            connections_url = f"{base_url}/llu/connections"
            conn_response = requests.get(connections_url, headers=headers, timeout=15)
            if conn_response.status_code != 200:
                last_error = f"ERR_HTTP_CONN {conn_response.status_code}: {conn_response.text[:120]}"
                continue

            conn_data = conn_response.json()
            lista_conexiones = conn_data.get("data", [])
            if not lista_conexiones:
                return "🩸 SIN_CONEXIONES"

            selected_connection = None
            patient_id = None
            for item in lista_conexiones:
                if PATIENT_NAME:
                    nombre = " ".join(
                        filter(
                            None,
                            [
                                item.get("firstName"),
                                item.get("lastName"),
                                item.get("patientName"),
                                item.get("name"),
                            ],
                        )
                    ).lower()
                    if PATIENT_NAME.lower() in nombre:
                        selected_connection = item
                        patient_id = item.get("patientId")
                        break
                else:
                    selected_connection = item
                    patient_id = item.get("patientId")
                    if patient_id:
                        break

            if not patient_id and lista_conexiones:
                selected_connection = lista_conexiones[0]
                patient_id = selected_connection.get("patientId")

            if not patient_id:
                return "🩸 ERR_PATIENT_ID"

            # 3. Lectura: primero intentamos usar los datos del propio endpoint de conexiones.
            current_glucose = None
            if selected_connection:
                current_glucose = (
                    selected_connection.get("glucoseMeasurement")
                    or selected_connection.get("glucoseItem")
                    or (selected_connection.get("connection") or {}).get("glucoseMeasurement")
                    or (selected_connection.get("connection") or {}).get("glucoseItem")
                )

            if not current_glucose:
                glucose_url = f"{base_url}/llu/connections/{patient_id}/graph?minutes=60"
                gluc_response = requests.get(glucose_url, headers=headers, timeout=15)
                if gluc_response.status_code != 200:
                    last_error = f"ERR_HTTP_GLUCOSE {gluc_response.status_code}: {gluc_response.text[:120]}"
                    continue

                gluc_data = gluc_response.json()
                current_glucose = (gluc_data.get("data") or {}).get("connection", {}).get("glucoseMeasurement")

            if not current_glucose:
                return "🩸 SIN_DATOS"

            valor = current_glucose.get("Value")
            valor_ajustado = calcular_valor_ajustado(valor)
            valor_texto = formatear_valor(valor)
            valor_ajustado_texto = formatear_valor(valor_ajustado if valor_ajustado is not None else valor)
            trend_map = {
                0: "⏬",
                1: "⬇️",
                2: "↘️",
                3: "➡️",
                4: "↗️",
                5: "⬆️",
                6: "⏫",
            }
            trend = trend_map.get(current_glucose.get("TrendArrow"), "•")

            return f"🩸 {trend} {valor_texto}({valor_ajustado_texto})"

        except Exception as e:
            last_error = f"ERROR: {str(e)}"

    return f"🩸 {last_error or 'ERROR'}"


def main():
    if NOTIFY_ENABLED:
        while True:
            nuevo_dato = obtener_desde_servidor()
            if nuevo_dato:
                send_notification(nuevo_dato)
            time.sleep(NOTIFY_INTERVAL_SECONDS)

    if USE_CACHE and os.path.exists(CACHE_FILE):
        file_age = time.time() - os.path.getmtime(CACHE_FILE)
        if file_age < CACHE_DURATION:
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                output = f.read().strip()
                if output:
                    print(output)
                    return

    nuevo_dato = obtener_desde_servidor()

    if nuevo_dato:
        if USE_CACHE:
            with open(CACHE_FILE, "w", encoding="utf-8") as f:
                f.write(nuevo_dato)
        print(nuevo_dato)
    else:
        if USE_CACHE and os.path.exists(CACHE_FILE):
            with open(CACHE_FILE, "r", encoding="utf-8") as f:
                print(f.read().strip() + " (old)")
        else:
            print("🩸 --")


if __name__ == "__main__":
    main()
