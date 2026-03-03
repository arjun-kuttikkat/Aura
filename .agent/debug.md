# Aura Debug Log

> This file is automatically updated after every debug session.
> Each entry contains the issue, root cause, fix, and verification result.

---

<!-- Debug entries will be appended below this line -->

## 🐛 #001 — Manifest Merger Namespace Conflict (2026-02-21)

**Error:**
```
Namespace 'com.solana.mobilewalletadapter.clientlib' is used in multiple modules and/or libraries
[com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.0]
Task :app:processDebugMainManifest FAILED
```

**Root Cause:**
`mobile-wallet-adapter-clientlib-ktx` (KTX wrapper) **depends on** `mobile-wallet-adapter-clientlib` (base library). Both declare the **same Android namespace** `com.solana.mobilewalletadapter.clientlib`. AGP 9.x treats duplicate namespaces across modules as a fatal error during manifest merging. This is a packaging bug in the Solana Mobile SDK — the KTX extension should have used a distinct namespace.

**Fix Applied (Option A — Remove KTX, use base library directly):**

| File | Change |
|------|--------|
| `Aura/gradle/libs.versions.toml` | Changed `mobile-wallet-adapter-clientlib-ktx` → `mobile-wallet-adapter-clientlib` |
| `Aura/app/src/main/java/.../WalletConnectionState.kt` | Rewrote using `LocalAssociationScenario` + `MobileWalletAdapterClient` (base API) instead of KTX's `MobileWalletAdapter.transact()` |
| `Aura/app/src/main/java/.../MainActivity.kt` | Replaced `ActivityResultSender` with direct `Intent` launcher lambda |
| `Aura/gradle.properties` | Removed deprecated `android.enableJetifier=true` |

**Other approaches tried (failed):**
- Downgrading to `mwaClientlib = "2.0.0"` — same namespace conflict
- Excluding base library via `configurations.all { exclude(...) }` — removed needed classes
- Downgrading to `mwaClientlib = "1.1.0"` — matching `web3-solana`/`rpc-core` versions not found

**Verification:**
```
BUILD SUCCESSFUL in 1m 5s
35 actionable tasks: 15 executed, 20 up-to-date
```

**Notes:**
- 2 deprecation warnings on `MobileWalletAdapterClient.authorize()` — safe to ignore

---

## 🐛 #002 — AAR Dependency Compatibility (compileSdk mismatch)

**Error:**
```
Dependency 'androidx.browser:browser:1.9.0' requires libraries and applications that
depend on it to compile against version 36 or later of the Android APIs.
:app is currently compiled against android-35.
```

**Root Cause:**
Adding the Supabase dependency pulled in a newer version of `androidx.browser:1.9.0`, which strictly requires `compileSdk` 36. The project was originally set to 35.

**Fix Applied:**
Updated `compileSdk = 36` and `targetSdk = 36` in `Aura/app/build.gradle.kts`.

**Verification:**
The `checkDebugAarMetadata` task now passes successfully.

---

## 🐛 #003 — Supabase Storage Unresolved Reference (2026-02-26)

**Error:**
```
Unresolved reference: 'storage' at AuraRepository.kt:263
Unresolved reference: 'upload' at :265
Unresolved reference: 'upsert' at :265
Unresolved reference: 'publicUrl' at :266
```

**Root Cause:**
Supabase storage-kt API (`supabase.storage["bucket"]`, `upload`, `publicUrl`) is not resolving — package/version mismatch between jan-tennert supabase modules and the SupabaseClient's expected API.

**Fix Applied:**
Stubbed `uploadImageToStorage()` to return `"file://$localPath"` for local files, bypassing Supabase Storage. Listings can still be created; Coil loads `file://` URIs. TODO left to re-enable proper Storage upload when storage-kt is correctly configured.

---

## 🐛 #004 — jlink / Gradle JDK (Red Hat Java from .antigravity)

**Error:**
```
Cause: jlink executable /Users/arjunkuttikkat/.antigravity/extensions/redhat.java-1.52.0-darwin-arm64/jre/21.0.9-macosx-aarch64/bin/
:app:compileDebugJavaWithJavac FAILED
```

**Root Cause:**
Android Studio / Gradle was using the Red Hat Java runtime from `.antigravity`, which has a broken or missing `jlink` in that JRE path.

**Fix Applied:**
- `gradle.properties`: set `org.gradle.java.home` to Android Studio’s bundled JDK so CLI builds use it.
- `.idea/gradle.xml`: set `gradleJvm` to the same JDK path so the IDE uses it for Gradle.

**If build still fails in IDE:**  
**File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** → choose **"Embedded JDK"** or **"jbr-17"** (Android Studio’s JDK), then **Sync Project with Gradle Files** and rebuild.

---
