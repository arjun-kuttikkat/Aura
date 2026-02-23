---
name: supabase-kotlin
description: How to use Supabase with Kotlin/Android in the Aura project
---

# Supabase Kotlin Integration for Aura

## Dependencies (add to libs.versions.toml)

```toml
[versions]
supabase = "3.1.1"
ktor = "3.1.1"

[libraries]
supabase-postgrest = { group = "io.github.jan-tennert.supabase", name = "postgrest-kt", version.ref = "supabase" }
supabase-auth = { group = "io.github.jan-tennert.supabase", name = "auth-kt", version.ref = "supabase" }
supabase-storage = { group = "io.github.jan-tennert.supabase", name = "storage-kt", version.ref = "supabase" }
supabase-realtime = { group = "io.github.jan-tennert.supabase", name = "realtime-kt", version.ref = "supabase" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
```

## Supabase Client Initialization

```kotlin
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime

object SupabaseClient {
    val client = createSupabaseClient(
        supabaseUrl = "https://hwxfqdatmhpdtpugxuxr.supabase.co",
        supabaseKey = "YOUR_ANON_KEY_HERE"
    ) {
        install(Postgrest)
        install(Auth)
        install(Storage)
        install(Realtime)
    }
}
```

## Common Patterns

### Query Data
```kotlin
val listings = client.postgrest["listings"]
    .select()
    .decodeList<Listing>()
```

### Insert Data
```kotlin
client.postgrest["listings"]
    .insert(newListing)
```

### Update Data
```kotlin
client.postgrest["listings"]
    .update({ set("title", newTitle) }) {
        filter { eq("id", listingId) }
    }
```

### Upload Image
```kotlin
val bucket = client.storage["listing-images"]
bucket.upload("path/image.jpg", imageBytes)
val url = bucket.publicUrl("path/image.jpg")
```

### Realtime Subscription
```kotlin
val channel = client.realtime.channel("trade-updates")
val flow = channel.postgresChangeFlow<PostgresAction>("public") {
    table = "trade_sessions"
}
channel.subscribe()
flow.collect { change -> /* handle update */ }
```

## Aura-Specific Tables
- `users` — User profiles, wallet addresses, Aura Score
- `listings` — Marketplace listings with images
- `trade_sessions` — Active trades between buyer/seller
- `aura_scores` — Daily score history and decay tracking
- `rewards` — User reward points and history
