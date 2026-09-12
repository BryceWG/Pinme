# =====================================================
# PinMe ProGuard/R8 Rules - 精简版
# =====================================================

# Kotlin Serialization - 只保留序列化相关
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class **$$serializer {
    *** INSTANCE;
}

# Room - 只保留实体注解
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# OkHttp - 最小化规则
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ML Kit barcode + AGP 9 R8 full mode
# BarcodeScanning.getClient() 会在 ComponentRegistrar / 内部类被剥离时 NPE：
# Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()'
# https://github.com/googlesamples/mlkit/issues/1018
-dontwarn com.google.mlkit.**
-keep class * implements com.google.firebase.components.ComponentRegistrar { void <init>(); }
-keep,allowshrinking interface com.google.firebase.components.ComponentRegistrar
-keep class com.google.mlkit.common.** { *; }
-keep class com.google.mlkit.vision.barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }

# 移除 release 版本的日志
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
