# =====================================================
# PinMe ProGuard/R8 Rules
# =====================================================

# =====================================================
# Kotlin
# =====================================================
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# =====================================================
# Kotlin Serialization
# =====================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# =====================================================
# Room Database
# =====================================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# =====================================================
# OkHttp
# =====================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# =====================================================
# ML Kit Barcode Scanning
# =====================================================
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# =====================================================
# Glance AppWidget
# =====================================================
-keep class androidx.glance.** { *; }

# =====================================================
# Project-specific: Keep data classes used in JSON parsing
# =====================================================
-keep class com.brycewg.pinme.db.** { *; }
-keep class com.brycewg.pinme.extract.** { *; }
-keep class com.brycewg.pinme.vllm.** { *; }

# =====================================================
# Remove logging in release builds (optional, reduces size)
# =====================================================
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
