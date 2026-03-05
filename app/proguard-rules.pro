# ProGuard/R8 rules for Aura
# ══════════════════════════════════════════════════════════════════

# ── R8 Full Mode ──────────────────────────────────────────────────
-allowaccessmodification
-repackageclasses ''

# ── Kotlinx Serialization ─────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.aura.app.model.**$$serializer { *; }
-keepclassmembers class com.aura.app.model.** {
    *** Companion;
}
-keep class com.aura.app.data.*Dto { *; }
-keep class com.aura.app.data.*Row { *; }

# ── Supabase / Ktor ───────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# ── OkHttp (Ktor engine) ─────────────────────────────────────────
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Solana Mobile Wallet Adapter ──────────────────────────────────
-keep class com.solana.mobilewalletadapter.** { *; }
-keep class com.funkatronics.** { *; }
-dontwarn com.solana.mobilewalletadapter.**
-dontwarn com.funkatronics.**

# ── Bouncy Castle / Crypto (ed25519 PDA derivation) ──────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── ML Kit Face Detection ─────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Google Play Services Location ─────────────────────────────────
-keep class com.google.android.gms.location.** { *; }
-keep class com.google.android.gms.maps.** { *; }

# ── ZXing QR ──────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }

# ── Coil Image Loading ────────────────────────────────────────────
-keep class coil.** { *; }

# ── Lottie ────────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }

# ── Jetpack Compose (keep for reflection in debuggable builds) ────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── CameraX ───────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── Keep line numbers for crash stack traces ──────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Prevent stripping of Android entry points ─────────────────────
-keep class com.aura.app.MainActivity { *; }
-keep class com.aura.app.util.AuraHceService { *; }