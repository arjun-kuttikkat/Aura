@file:OptIn(io.github.jan.supabase.annotations.SupabaseInternal::class)
package com.aura.app.data

import com.aura.app.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.functions.Functions
import io.ktor.client.plugins.HttpTimeout
import android.content.Context

object SupabaseClient {
    private val supabaseUrl: String = run {
        val url = BuildConfig.SUPABASE_URL
        when {
            url.isBlank() -> throw IllegalStateException(
                "SUPABASE_URL is not set. Add SUPABASE_URL=https://your-project.supabase.co to local.properties and rebuild."
            )
            url.contains("localhost", ignoreCase = true) || url.contains("127.0.0.1") ->
                throw IllegalStateException(
                    "SUPABASE_URL must point to your hosted Supabase project, not localhost. " +
                        "Use https://your-project.supabase.co in local.properties. " +
                        "localhost only works on the host machine, not on devices."
                )
            !url.startsWith("https://") -> throw IllegalStateException(
                "SUPABASE_URL must use https:// (e.g. https://your-project.supabase.co)"
            )
            else -> url
        }
    }

    var appContext: Context? = null
    val client = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        // Install HttpTimeout — generous defaults for auth/edge functions (cold starts, flaky networks)
        httpConfig {
            install(HttpTimeout) {
                requestTimeoutMillis = 90_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 90_000
            }
        }
        install(Postgrest)
        install(Auth)
        install(Storage)
        install(Realtime)
        install(Functions)
    }
}
