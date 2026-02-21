# Aura Project Rules

## Project Overview
- **Name**: Aura — P2P Marketplace with Trust & Escrow
- **Type**: Native Android Application
- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM (Model-View-ViewModel)
- **Blockchain**: Solana (Mobile Wallet Adapter, web3-solana, rpc-core)
- **Database**: Supabase (PostgreSQL + Auth + Storage + Realtime)
- **Min SDK**: 26 (Android 8.0), Target SDK: 36

## Tech Stack Quick Reference
- **Image Loading**: Coil 2.5.0
- **Camera**: CameraX 1.5.2
- **Face Detection**: Google ML Kit 16.1.6
- **QR Codes**: ZXing 3.5.3
- **Permissions**: Accompanist 0.34.0
- **Wallet**: Solana MWA 2.0.3

## Coding Conventions
1. **All UI** must use Jetpack Compose (`@Composable` functions), never XML layouts.
2. **State management** uses `StateFlow` and `collectAsState()` in composables.
3. **Navigation** uses `NavGraph.kt` and `Routes.kt` — all routes defined in `Routes` object.
4. **Colors** use the project theme (`ui/theme/Color.kt`, `Theme.kt`, `Type.kt`).
5. **Data layer**: Currently using `MockBackend.kt` — being migrated to Supabase.
6. **Package structure**: `com.aura.app` with subdirectories: `data/`, `model/`, `navigation/`, `ui/screen/`, `ui/components/`, `ui/theme/`, `wallet/`, `util/`.
7. **Dependencies** are managed in `gradle/libs.versions.toml` using version catalogs.

## Supabase Configuration
- **Project URL**: `https://hwxfqdatmhpdtpugxuxr.supabase.co`
- **Kotlin SDK**: `io.github.jan-tennert.supabase` (supabase-kt)
- Use `io.ktor` for the HTTP engine on Android.

## Key Decisions
- Off-chain data (profiles, listings, images) → Supabase
- On-chain data (escrow, fees, Aura Score proof) → Solana programs
- Face scan uses ML Kit locally, ZK proof is generated client-side
- Mock data should be maintained alongside real Supabase calls during development
