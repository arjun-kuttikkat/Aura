<div align="center">
  <img src="logo.png" alt="Aura Logo" width="200"/>
  
  # Aura

  **The Physical-to-Digital Marketplace — Trustless P2P Commerce on Solana**

  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-blue.svg)](https://kotlinlang.org/)
  [![Android](https://img.shields.io/badge/Android-26+-green.svg)](https://www.android.com/)
  [![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-orange.svg)](https://developer.android.com/jetpack/compose)
  [![Solana](https://img.shields.io/badge/Solana-Anchor%200.29-purple.svg)](https://solana.com/)
  [![NFC](https://img.shields.io/badge/NFC-NTAG%20424%20DNA-red.svg)](https://www.nxp.com/products/rfid-nfc/nfc-hf/ntag/ntag-424-dna:NTAG424DNA)
  [![Metaplex](https://img.shields.io/badge/Metaplex-Core%20NFT-yellow.svg)](https://www.metaplex.com/)
  [![License](https://img.shields.io/badge/License-MIT-brightgreen.svg)](LICENSE)
</div>

---

> **Hackathon Submission** — Aura was built for the Solana hackathon to prove that
> *real-world commerce can be as trustless as DeFi*. Every line of code, from the
> Anchor escrow to the NTAG 424 DNA CMAC verifier, targets a single goal:
> **zero-trust physical handovers, settled on-chain in seconds.**

---

## 🎯 The Problem

Peer-to-peer marketplaces like Craigslist and Facebook Marketplace have a **$200B+ trust problem**:

| Pain Point | Status Quo | Aura's Answer |
|---|---|---|
| **Item Authenticity** | Buyers can't verify before meeting | AI photo check + NFC crypto tag |
| **Payment Risk** | Cash has zero fraud protection | SOL locked in Anchor escrow PDA |
| **No-shows / Scams** | No accountability | On-chain Aura Score reputation |
| **Ownership Transfer** | "I already sold it" disputes | Metaplex Core NFT receipt on-chain |

## 💡 The Solution: Aura

Aura is a **Solana-native Android marketplace** that uses **NFC cryptographic verification**, **on-chain escrow**, and **Metaplex Core NFTs** to create trustless, fraud-proof physical goods transactions. Every handover is cryptographically verified, funds are protected by smart contracts, and ownership is permanently recorded on-chain.

---

## 🔄 How It Works

```
 ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
 │  1. LIST │───▶│ 2. MATCH │───▶│ 3. MEET  │───▶│ 4. TAP   │───▶│ 5. DONE  │
 │  Photo + │    │ Buyer    │    │ ML Kit   │    │ NTAG 424 │    │ Escrow   │
 │  Price   │    │ funds    │    │ liveness │    │ DNA CMAC │    │ releases │
 │          │    │ escrow   │    │ check    │    │ verified │    │ NFT mint │
 └──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
```

1. **LIST** — Seller photographs item, sets price in SOL, AI verifies photo authenticity
2. **MATCH** — Buyer funds Anchor escrow (SOL locked in a vault PDA on-chain)
3. **MEET** — Both parties meet in person; ML Kit face liveness confirms identity
4. **TAP** — NTAG 424 DNA tag produces a SUN URL with AES-128-CMAC signature
5. **DONE** — Edge Function verifies CMAC proof → escrow releases SOL → Metaplex Core NFT mints

> **Key Innovation:** The NFC tap creates a **cryptographic proof-of-handover** that is
> verified server-side before funds are released. No trust required — only math.

---

## 🏗️ Architecture

```mermaid
graph TD
    A["📱 Android App<br/>Kotlin + Jetpack Compose"] --> B["🔐 Solana MWA<br/>Mobile Wallet Adapter 2.1"]
    A --> C["📡 NFC<br/>NTAG 424 DNA"]
    A --> D["🤖 ML Kit<br/>Face Liveness"]
    A --> R["📡 Supabase Realtime<br/>Trade-session updates"]
    
    B --> E["⚓ Anchor Escrow<br/>initialize + release_funds_and_mint"]
    C --> F["☁️ Supabase Edge Functions"]
    
    F --> G["verify-sun<br/>AES-128-CMAC<br/>Session key derivation"]
    F --> H["mint-nft<br/>Metaplex Core via UMI"]
    F --> I["rpc-proxy<br/>Helius RPC"]
    F --> J["blinks-action<br/>Solana Actions + Idempotency"]
    F --> P["verify-photo<br/>Groq Vision AI"]
    
    G --> E
    H --> K["🖼️ Metaplex Core NFT<br/>Proof of Ownership"]
    E --> L["💰 Vault PDA<br/>SOL Escrow"]
    
    style A fill:#1a1a2e,color:#fff
    style E fill:#9945FF,color:#fff
    style K fill:#FFD700,color:#000
    style L fill:#14F195,color:#000
```

---

## ✨ Features

### Core Marketplace
- 🏪 **Listings Grid** — Browse items with real-time pricing in SOL, pull-to-refresh
- 📸 **Camera Capture** — CameraX-powered photo capture with macro texture scanning
- 🔐 **Anchor Escrow** — SOL locked in vault PDA until cryptographic verification completes
- 📲 **NFC Handover** — NTAG 424 DNA SUN URL with AES-128-CMAC signature verification
- 🖼️ **NFT Receipt** — Metaplex Core NFT minted on trade completion as proof of ownership
- 💬 **In-app Chat** — Real-time messaging between buyer and seller via Supabase Realtime

### Trust & Security Layer
- 👤 **Face Liveness** — Google ML Kit real-time biometric verification at meetup
- 🔍 **Aura Check** — AI-powered item authenticity scanning via Groq Vision (llama-4-scout)
- 📊 **Aura Score** — On-chain reputation system based on verified trade history
- 🔥 **Streak Tracking** — Gamified engagement with daily scan streaks
- 🛡️ **Risk Oracle** — Per-trade risk assessment using seller Aura Score before committing funds
- ✅ **Confirmation Dialogs** — Escrow release, wallet disconnect, and trade start all require explicit confirmation

### Solana Native
- 💼 **Mobile Wallet Adapter 2.1** — Native connection to Phantom, Solflare, and other MWA wallets
- ⚡ **Solana Blinks** — Share listings as executable Actions on Twitter/Discord with idempotency guard
- 🏗️ **Client-side PDA Derivation** — Anchor PDA computation for escrow + vault without RPC calls
- 📡 **Helius RPC** — Production-grade RPC via Edge Function proxy (no exposed API keys)

### Gamification & Engagement
- 🎯 **Directives** — Gamified task challenges (Spatial Sweep, Guardian Witness, Texture Archive)
- 🏆 **Rewards** — XP, badges, and tier progression
- 🗺️ **Hotzones** — H3-indexed geographic trading zones with turf leaderboards
- ⚙️ **Settings** — Notifications, appearance, security, privacy sub-screens

---

## 🔒 Security Model

| Layer | Protection |
|-------|-----------|
| **Funds** | SOL locked in Anchor PDA vault; only released after server-verified NFC proof |
| **NFC** | NTAG 424 DNA: AES-128-CMAC with session key derivation (SV2 counter-bound) |
| **Database** | Row Level Security on every table; JWT `wallet_address` claim via `requesting_wallet()` |
| **Secrets** | All API keys in `local.properties` → BuildConfig; never committed to VCS |
| **RPC** | Helius endpoint proxied through Edge Function; client never sees raw key |
| **Release Build** | R8 full mode, ProGuard rules for OkHttp / Bouncy Castle / Compose / CameraX |
| **Photo Verify** | Groq Vision AI validates item photos server-side before listing is accepted |

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin 2.0 |
| **UI** | Jetpack Compose + Material3 + Lottie |
| **Blockchain** | Solana (MWA 2.1, Anchor, Metaplex Core) |
| **Smart Contract** | Rust / Anchor Framework |
| **NFC** | NTAG 424 DNA (IsoDep + HCE) |
| **Biometrics** | Google ML Kit Face Detection + CameraX |
| **Backend** | Supabase (PostgREST, Auth, Storage, Realtime, Edge Functions) |
| **RPC** | Helius (via Edge Function proxy) |
| **QR Codes** | ZXing |
| **Image Loading** | Coil |
| **Animations** | Lottie + Spring Physics |
| **Data** | DataStore Preferences |

---

## 📁 Project Structure

```
├── app/src/main/java/com/aura/app/
│   ├── data/                    # Repository, Supabase clients, managers
│   │   ├── AuraRepository.kt   # Central CRUD (listings, trades, escrow, profiles)
│   │   ├── SupabaseClient.kt   # Supabase initialization
│   │   ├── DirectivesManager.kt
│   │   ├── HotzoneManager.kt
│   │   └── TradeRiskOracle.kt
│   ├── model/                   # Domain models (11 files)
│   ├── navigation/              # NavGraph + Routes (17 destinations)
│   ├── ui/
│   │   ├── components/          # AppLogo, AuraComponents, CoreRenderer, ShimmerEffect
│   │   ├── screen/              # 17 screens (Onboarding → TradeComplete)
│   │   ├── theme/               # Glassmorphism design system
│   │   └── util/                # HapticEngine, springScale
│   ├── util/                    # NfcHandoverManager, AuraHceService, FaceAnalyzer
│   └── wallet/                  # WalletConnectionState, AnchorTransactionBuilder, SolanaRpc
├── smart_contracts/
│   └── aura_escrow/programs/    # Anchor Rust program (initialize + release_funds_and_mint)
├── supabase/
│   ├── functions/               # 7 Edge Functions
│   │   ├── verify-sun/          # NFC CMAC verification + escrow release
│   │   ├── mint-nft/            # Metaplex Core minting via UMI
│   │   ├── blinks-action/       # Solana Actions (Twitter/Discord unfurl)
│   │   ├── rpc-proxy/           # Helius RPC proxy
│   │   ├── verify-photo/
│   │   ├── aura-core-nft/
│   │   └── mint-aura-token/
│   └── migrations/              # PostgreSQL schema + RLS policies
```

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug or later
- JDK 17+
- Android SDK 26+ (Android 8.0+)
- Solana wallet app (Phantom / Solflare) on device

### Build & Run

```bash
git clone https://github.com/arjun-kuttikkat/Aura.git
cd Aura
./gradlew assembleDebug
```

### Supabase Setup

1. Create a Supabase project
2. Run `supabase/migrations/001_schema.sql` in SQL Editor
3. Deploy edge functions: `supabase functions deploy`
4. Set env vars: `NFC_MASTER_AES_KEY`, `SOLANA_AUTHORITY_KEY`, `HELIUS_API_KEY`

### Groq AI Setup

Add to `local.properties` (copy from `local.properties.example`):

```properties
GROQ_API_KEY=your-groq-api-key
GROQ_MODEL=meta-llama/llama-4-scout-17b-16e-instruct
```

Get an API key at [console.groq.com](https://console.groq.com). The model is optional — it defaults to `meta-llama/llama-4-scout-17b-16e-instruct`.

---

## 👥 Team

- **Arjun Kuttikkat** — Architecture, Solana Integration, Smart Contracts

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

<div align="center">
  <b>Built on Solana · Verified by NFC · Secured by Anchor</b>
</div>
