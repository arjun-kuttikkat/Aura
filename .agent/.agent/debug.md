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
