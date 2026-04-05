# ============================================================
# VARYNX 2.0 — ProGuard / R8 Rules
# ============================================================

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── kotlinx-serialization ────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class com.varynx.varynx20.** {
    *** Companion;
    *** serializer(...);
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Ktor ─────────────────────────────────────────────────────
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# ── ML Kit Barcode ───────────────────────────────────────────
-keep class com.google.mlkit.vision.barcode.** { *; }
-dontwarn com.google.mlkit.**

# ── CameraX ──────────────────────────────────────────────────
-keep class androidx.camera.** { *; }

# ── Java Crypto ──────────────────────────────────────────────
-keep class javax.crypto.** { *; }
-keep class java.security.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── VARYNX core mesh (serialized protocol messages) ─────────
-keep class com.varynx.varynx20.core.mesh.** { *; }
-keep class com.varynx.varynx20.core.model.** { *; }
-keep class com.varynx.varynx20.core.protection.DetectionSignal { *; }

# ── IPC protocol classes ─────────────────────────────────────
-keep class com.varynx.service.ipc.** { *; }

# ── Kotlin coroutines ────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Compose (already handled by AGP but explicit for safety) ─
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── DataStore ────────────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}