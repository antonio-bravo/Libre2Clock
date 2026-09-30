import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.time.Instant

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.google.services)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        // Optimizaciones del compilador de Kotlin para mejor rendimiento
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.RequiresOptIn",
            "-Xjvm-default=all",
            "-Xbackend-threads=0"
        )
    }
}

android {
    namespace = "com.tonio.libre2clock"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.tonio.libre2clock"
        minSdk = 28
        targetSdk = 36
        
        val appVersion = project.findProperty("appVersion")?.toString() ?: "1.local"
        versionName = appVersion
        
        // Generate a versionCode from YYYYMMDDHH format (fits in Int)
        val datePart = DateTimeFormatter.ofPattern("yyyyMMddHH")
            .withZone(ZoneId.of("UTC"))
            .format(Instant.now())
        versionCode = datePart.toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTORE_PATH")
            if (keystorePath != null && file(keystorePath).exists()) {
                val pass = System.getenv("KEYSTORE_PASSWORD")
                storeFile = file(keystorePath)
                storePassword = pass
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD") ?: pass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true

            // Solo usar signing config si está configurado correctamente
            val releaseSigningConfig = signingConfigs.getByName("release")
            if (releaseSigningConfig.storeFile != null) {
                signingConfig = releaseSigningConfig
            }

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Optimizaciones adicionales para reducir tamaño y mejorar rendimiento
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
                // Solo incluir arquitecturas ARM (las más comunes en dispositivos móviles)
                // Esto evita incluir librerías nativas para x86/x86_64 (emuladores/tablets Intel)
                abiFilters.clear()
                abiFilters.add("arm64-v8a")
                abiFilters.add("armeabi-v7a")
            }
        }

        debug {
            // Debug más ligero para desarrollo en dispositivos antiguos
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true

        // Deshabilitar features no usadas para reducir overhead
        aidl = false
        resValues = false
        shaders = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    // Eliminado: material-icons-extended (añadía ~20-25MB con 2000+ iconos sin usar)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.config)
    implementation(libs.play.services.auth)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.converter.moshi)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.logging.interceptor)
    implementation(libs.moshi.kotlin)
    implementation(libs.okhttp)
    implementation(libs.retrofit)
    testImplementation(libs.androidx.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}