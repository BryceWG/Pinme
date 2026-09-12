# =====================================================
# PinMe ProGuard/R8 Rules
# AGP 9 默认 R8 full mode：库内部反射 + 应用 Glance/序列化
# 都必须显式 keep，否则正式包会出现扫码 NPE、小组件卡在「加载中」。
# =====================================================

-keepattributes *Annotation*, InnerClasses, Signature, Exception, SourceFile, LineNumberTable, RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }

# 应用代码一律保留。体积主要来自三方库，R8 仍会压缩 OkHttp/Compose/ML Kit。
-keep class com.brycewg.pinme.** { *; }

# ---------- Kotlinx Serialization ----------
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
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

-keep,includedescriptorclasses class **$$serializer { *; }

# ---------- Glance 小组件 ----------
# ActionCallback / Widget 由 Glance 按类名反射构造，构造器被剥就会一直停在 initialLayout。
-keep class * extends androidx.glance.appwidget.GlanceAppWidget {
    <init>();
}
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver {
    <init>();
}
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
-keep class androidx.glance.appwidget.** { *; }

# Glance 用 WorkManager 刷新小组件。R8 full mode 会剥掉 InputMerger 无参构造，
# 日志：InstantiationException: OverwritingInputMerger has no zero argument constructor
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.InputMerger {
    <init>();
}
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# ---------- OkHttp ----------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------- ML Kit barcode + AGP 9 R8 full mode ----------
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

# 移除 release 版本的啰嗦日志，保留 w/e 便于正式包排障
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
