import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Load local.properties for secrets
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace = "com.aura.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.aura.app"
        minSdk = 26
        targetSdk = 36
        multiDexEnabled = true
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "HELIUS_KEY", "\"${localProps.getProperty("HELIUS_API_KEY", "")}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${localProps.getProperty("SUPABASE_URL", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${localProps.getProperty("SUPABASE_KEY", "")}\"")
        buildConfigField("String", "GROQ_API_KEY", "\"${localProps.getProperty("GROQ_API_KEY", "")}\"")
        buildConfigField("String", "GROQ_MODEL", "\"${localProps.getProperty("GROQ_MODEL", "meta-llama/llama-4-scout-17b-16e-instruct")}\"")
        buildConfigField("String", "CEREBRAS_API_KEY", "\"${localProps.getProperty("CEREBRAS_API_KEY", "")}\"")
        manifestPlaceholders["MAPS_API_KEY"] = localProps.getProperty("MAPS_API_KEY", "PLACEHOLDER_KEY")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.animation:animation")
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.zxing.core)
    implementation(libs.coil.compose)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)


    // Solana Mobile Wallet Adapter
    implementation(libs.solana.mwa)
    implementation(libs.solana.web3)
    implementation(libs.solana.rpc)
    implementation(libs.solana.multimult)
    
    // ML Kit Face Detection
    implementation(libs.mlkit.face.detection)
    implementation("com.google.mlkit:image-labeling:17.0.9")


    // Coroutines Guava
    implementation(libs.kotlinx.coroutines.guava)

    // Supabase (single declaration via version catalog)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Encrypted Storage
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Accompanist Permissions
    implementation(libs.accompanist.permissions)

    // Animation & Persistence
    implementation(libs.lottie.compose)
    implementation(libs.datastore.preferences)

    // New Additions: Maps, 3D Avatars, Confetti Animations
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.sceneview.arsceneview)
    implementation(libs.konfetti.compose)
    
    // Uber H3 Spatial Indexing
    implementation("com.uber:h3:4.1.1")

    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts:1.6.1")

    // GPS / Location — Hotzone Turf Wars
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-tasks:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}