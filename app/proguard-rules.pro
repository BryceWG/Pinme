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

# ML Kit - 只保留必要的
-dontwarn com.google.mlkit.**

# 移除 release 版本的日志
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
