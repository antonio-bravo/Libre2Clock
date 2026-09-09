# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

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
-keepclassmembers class com.tonio.libre2clock.data.model.** { *; }
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
