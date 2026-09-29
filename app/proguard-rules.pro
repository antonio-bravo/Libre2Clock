# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Aggressive optimizations for performance and size
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5
-allowaccessmodification
-dontpreverify

# Keep stack traces readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# CRITICAL: Keep Compose runtime classes
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }
-keep class androidx.compose.foundation.** { *; }
-keep class androidx.compose.material3.** { *; }
-keep class androidx.compose.material.** { *; }

# Keep all @Composable functions
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Keep Kotlin coroutines
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class androidx.datastore.** { *; }

# Remove logging in production to save space and improve performance
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
    public static int w(...);
    public static int e(...);
}

# Kotlin optimizations
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Keep all ViewModel classes
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }

# IMPORTANT: Don't remove null checks in debug/release for stability
# Comment out these lines if you want maximum optimization (risky)
# -assumenosideeffects class kotlin.jvm.internal.Intrinsics {
#     public static void checkNotNull(...);
#     public static void checkParameterIsNotNull(...);
#     public static void checkNotNullParameter(...);
#     public static void checkExpressionValueIsNotNull(...);
#     public static void checkNotNullExpressionValue(...);
#     public static void checkReturnedValueIsNotNull(...);
#     public static void checkFieldIsNotNull(...);
# }

# OkHttp checks optional TLS provider implementations that are not packaged on Android.
-dontwarn org.bouncycastle.jsse.BCSSLParameters
-dontwarn org.bouncycastle.jsse.BCSSLSocket
-dontwarn org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
-dontwarn org.conscrypt.Conscrypt$Version
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.ConscryptHostnameVerifier
-dontwarn org.openjsse.javax.net.ssl.SSLParameters
-dontwarn org.openjsse.javax.net.ssl.SSLSocket
-dontwarn org.openjsse.net.ssl.OpenJSSE

# Missing transitive annotations from Tink/Google libraries
-dontwarn com.google.errorprone.annotations.**

# Keep LibreLinkUp API, Models and Sync
-keep class com.tonio.libre2clock.data.api.** { *; }
-keep class com.tonio.libre2clock.data.model.** { *; }
-keepclassmembers class com.tonio.libre2clock.data.model.** {
    <init>(...);
    *;
}
-keep class com.tonio.libre2clock.data.sync.** { *; }

# Moshi and Retrofit (Critical for API)
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class retrofit2.** { *; }

# Google and Firebase (Critical for Sync)
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class com.tonio.libre2clock.data.model.** {
    *** Companion;
    *** $serializer;
}

# Keep Google Credential Manager / Firebase
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Prevent obfuscation of credential types
-keepnames class com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
-keepnames class com.google.android.libraries.identity.googleid.GetGoogleIdOption

# Keep generated resources for Google Services
-keep class com.google.android.gms.common.api.internal.** { *; }
-keep class com.tonio.libre2clock.R$string { <fields>; }

# Moshi and Retrofit specific
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**
