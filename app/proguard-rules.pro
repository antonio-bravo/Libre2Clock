# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# Keep stack traces readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Remove logging in production to save space and improve performance
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
    public static int w(...);
    public static int e(...);
}

# Keep LibreLinkUp API, Models and Sync
-keep class com.tonio.libre2clock.data.api.** { *; }
-keep class com.tonio.libre2clock.data.model.** { *; }
-keepclassmembers class com.tonio.libre2clock.data.model.** {
    <init>(...);
    *;
}
-keep class com.tonio.libre2clock.data.sync.** { *; }

# Keep Firestore data models and annotated fields
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
    @com.google.firebase.firestore.PropertyName <methods>;
}

# Moshi and Retrofit (Critical for API)
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}
-keep class retrofit2.** { *; }

# Keep Kotlin Serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.tonio.libre2clock.data.model.** {
    *** Companion;
    *** $serializer;
}

# Prevent obfuscation of credential types for Google Identity
-keepnames class com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
-keepnames class com.google.android.libraries.identity.googleid.GetGoogleIdOption

# Keep generated resources for Google Services
-keep class com.tonio.libre2clock.R$string { <fields>; }
