# ProGuard/R8 rules for Aura
# ══════════════════════════════════════════════════════════════════

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

# ── Supabase / Ktor ───────────────────────────────────────────────
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn io.github.jan.supabase.**

# ── Solana Mobile Wallet Adapter ──────────────────────────────────
-keep class com.solana.mobilewalletadapter.** { *; }
-keep class com.funkatronics.** { *; }
-dontwarn com.solana.mobilewalletadapter.**
-dontwarn com.funkatronics.**

# ── ML Kit Face Detection ─────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Google Play Services Location ─────────────────────────────────
-keep class com.google.android.gms.location.** { *; }

# ── ZXing QR ──────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }

# ── Coil Image Loading ────────────────────────────────────────────
-keep class coil.** { *; }

# ── Lottie ────────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }

# ── Keep line numbers for crash stack traces ──────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile