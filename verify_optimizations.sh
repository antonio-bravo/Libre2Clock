#!/bin/bash

# Script de Verificación de Optimizaciones - Libre2Clock
# Ejecutar después de implementar las optimizaciones

echo "🔍 Verificando Optimizaciones de Libre2Clock..."
echo "================================================"
echo ""

# Colores para output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Función para verificar archivos
check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✓${NC} $2"
        return 0
    else
        echo -e "${RED}✗${NC} $2 - ARCHIVO NO ENCONTRADO"
        return 1
    fi
}

# Función para verificar contenido
check_content() {
    if grep -q "$2" "$1" 2>/dev/null; then
        echo -e "${GREEN}✓${NC} $3"
        return 0
    else
        echo -e "${YELLOW}⚠${NC} $3 - NO ENCONTRADO"
        return 1
    fi
}

echo "📁 Verificando Archivos de Configuración..."
echo "-------------------------------------------"
check_file "gradle.properties" "gradle.properties existe"
check_file "app/proguard-rules.pro" "proguard-rules.pro existe"
check_file "app/build.gradle.kts" "build.gradle.kts existe"
echo ""

echo "⚙️  Verificando Configuraciones de Gradle..."
echo "--------------------------------------------"
check_content "gradle.properties" "org.gradle.jvmargs=-Xmx4096m" "JVM heap aumentado a 4GB"
check_content "gradle.properties" "android.enableR8.fullMode=true" "R8 full mode habilitado"
check_content "gradle.properties" "android.nonTransitiveRClass=true" "R classes optimizado"
check_content "gradle.properties" "android.enableJetifier=false" "Jetifier deshabilitado"
echo ""

echo "🔒 Verificando Reglas de ProGuard..."
echo "-------------------------------------"
check_content "app/proguard-rules.pro" "-optimizationpasses 5" "5 pases de optimización"
check_content "app/proguard-rules.pro" "-allowaccessmodification" "Access modification permitido"
check_content "app/proguard-rules.pro" "assumenosideeffects class android.util.Log" "Logs eliminados en producción"
echo ""

echo "📱 Verificando AndroidManifest..."
echo "---------------------------------"
check_content "app/src/main/AndroidManifest.xml" "android:hardwareAccelerated=\"true\"" "Hardware acceleration habilitado"
check_content "app/src/main/AndroidManifest.xml" "android:largeHeap=\"false\"" "largeHeap configurado correctamente"
echo ""

echo "🗄️  Verificando Optimizaciones de Base de Datos..."
echo "---------------------------------------------------"
check_content "app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt" "enableWriteAheadLogging" "WAL mode habilitado"
check_content "app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt" "PRAGMA cache_size" "Cache size configurado"
check_content "app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt" "fun vacuum()" "Método vacuum() agregado"
check_content "app/src/main/java/com/tonio/libre2clock/data/local/GlucoseHistoryDatabaseHelper.kt" "fun analyze()" "Método analyze() agregado"
echo ""

echo "🚀 Verificando MainActivity..."
echo "------------------------------"
check_content "app/src/main/java/com/tonio/libre2clock/MainActivity.kt" "by lazy" "Lazy initialization implementado"
check_content "app/src/main/java/com/tonio/libre2clock/MainActivity.kt" "lifecycleScope" "lifecycleScope para init background"
echo ""

echo "📦 Verificando Build.gradle.kts..."
echo "----------------------------------"
check_content "app/build.gradle.kts" "freeCompilerArgs" "Kotlin compiler optimizations"
check_content "app/build.gradle.kts" "-Xjvm-default=all" "JVM default methods habilitado"
check_content "app/build.gradle.kts" "aidl = false" "Features innecesarias deshabilitadas"
echo ""

echo "🎨 Verificando Dependencias..."
echo "-------------------------------"
if grep -q "material-icons-extended" "app/build.gradle.kts" 2>/dev/null; then
    echo -e "${YELLOW}⚠${NC} material-icons-extended aún presente (considerar remover)"
else
    echo -e "${GREEN}✓${NC} material-icons-extended removido"
fi
echo ""

echo "📊 Intentando Medir Tamaño del APK..."
echo "--------------------------------------"
if [ -d "app/build/outputs/apk/release" ]; then
    APK_FILE=$(find app/build/outputs/apk/release -name "*.apk" -type f | head -n 1)
    if [ -n "$APK_FILE" ]; then
        APK_SIZE=$(du -h "$APK_FILE" | cut -f1)
        echo -e "${GREEN}✓${NC} APK encontrado: $APK_SIZE"
        echo "  Ruta: $APK_FILE"
    else
        echo -e "${YELLOW}⚠${NC} No se encontró APK. Ejecutar: ./gradlew assembleRelease"
    fi
else
    echo -e "${YELLOW}⚠${NC} No hay build de release. Ejecutar: ./gradlew assembleRelease"
fi
echo ""

echo "🔧 Comandos Útiles para Verificar:"
echo "-----------------------------------"
echo "1. Compilar release:     ./gradlew assembleRelease"
echo "2. Analizar APK:         ./gradlew assembleRelease && open app/build/outputs/apk/release/"
echo "3. Memoria en runtime:   adb shell dumpsys meminfo com.tonio.libre2clock"
echo "4. Startup time:         adb shell am start -W com.tonio.libre2clock/.MainActivity"
echo "5. Limpiar proyecto:     ./gradlew clean"
echo ""

echo "📝 Documentación Adicional:"
echo "---------------------------"
check_file "OPTIMIZATION_PLAN.md" "Plan de optimización"
check_file "PERFORMANCE_RECOMMENDATIONS.md" "Recomendaciones de rendimiento"
echo ""

echo "✅ Verificación Completada!"
echo "==========================="
echo ""
echo "💡 Próximos Pasos:"
echo "  1. Compilar en modo release: ./gradlew assembleRelease"
echo "  2. Probar en un dispositivo Android antiguo (API 28-29)"
echo "  3. Verificar memoria con Android Profiler"
echo "  4. Revisar PERFORMANCE_RECOMMENDATIONS.md para optimizaciones adicionales"
echo ""
