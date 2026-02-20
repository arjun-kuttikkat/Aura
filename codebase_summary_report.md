# Codebase Summary Report: Aura App

## 1. Project Overview

This repository contains the source code for "Aura", an Android application built with Kotlin and Jetpack Compose. The application appears to be a marketplace platform that facilitates secure, in-person trades or sales. It integrates several modern technologies, including facial recognition, NFC for data transfer, and a Solana crypto wallet for transactions, to ensure trust and security between users.

## 2. Project Structure

The project follows a standard Gradle structure for Android applications. The core application code resides within the `app` module.

```
clons2/
├── app/
│   ├── build.gradle.kts      # App-level build script
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml # Core Android app manifest
│       │   └── java/com/aura/app/
│       │       ├── MainActivity.kt         # Main entry point Activity
│       │       ├── data/                   # Data sources (Supabase, Mock data)
│       │       ├── model/                  # Data models (e.g., Listing, User)
│       │       ├── navigation/             # Jetpack Compose navigation graph
│       │       ├── ui/                     # UI layer (Compose screens and components)
│       │       │   ├── components/         # Reusable UI elements
│       │       │   └── screen/             # Individual app screens
│       │       ├── util/                   # Utility classes (FaceAnalyzer, NFC)
│       │       └── wallet/                 # Solana wallet integration
│       └── test/                   # Unit tests
└── gradle/                     # Gradle wrapper and configuration
```

-   **`app/`**: The main Android application module.
-   **`app/src/main/java/com/aura/app/`**: The root package for all Kotlin source code.
-   **`data/`**: Handles data operations. `SupabaseClient.kt` suggests it uses Supabase as a backend-as-a-service, while `MockBackend.kt` indicates the presence of a mock data source for development or testing.
-   **`model/`**: Contains the data classes that represent the application's core objects, such as `Listing`, `User`, and `TradeSession`.
-   **`navigation/`**: Defines the navigation structure of the app using Jetpack Compose Navigation. `NavGraph.kt` likely defines all the routes and their relationships.
-   **`ui/`**: The presentation layer, built entirely with Jetpack Compose. It's separated into reusable `components` and distinct `screen` composables.
-   **`util/`**: Home to utility and helper classes, notably `FaceAnalyzer.kt` for facial recognition and `NfcHandoverManager.kt` for NFC interactions.
-   **`wallet/`**: Contains the logic for interacting with the Solana blockchain, including RPC calls (`SolanaRpc.kt`).

## 3. Core Technologies

-   **Language**: Kotlin
-   **UI**: Jetpack Compose
-   **Build Tool**: Gradle
-   **Backend**: Supabase (a Firebase alternative)
-   **Database**: Likely PostgreSQL (managed by Supabase)
-   **Blockchain**: Solana
-   **Asynchronous Programming**: Kotlin Coroutines
-   **Navigation**: Jetpack Compose Navigation

## 4. Key Features

The codebase suggests a rich feature set focused on secure and verified transactions:

-   **Onboarding**: A flow for new users (`OnboardingScreen.kt`).
-   **Marketplace Listings**: Users can create (`CreateListingScreen.kt`) and view (`ListingDetailScreen.kt`, `HomeScreen.kt`) listings for items.
-   **User Profiles**: Users have profiles (`ProfileScreen.kt`).
-   **Facial Verification**: The app uses facial analysis (`FaceAnalyzer.kt`, `FaceVerificationScreen.kt`), likely to verify user identity before a trade.
-   **Item Verification**: A screen dedicated to verifying the item being traded (`VerifyItemScreen.kt`).
-   **Secure Trade Sessions**:
    -   **NFC Handover**: `NfcHandoverManager.kt` and `MeetSessionScreen.kt` suggest that users can initiate a trade session by tapping their phones together using NFC.
    -   **Escrow System**: The app includes an escrow-like payment flow (`EscrowPayScreen.kt`, `EscrowStatus.kt`) where funds are likely held until both parties confirm the trade.
-   **Solana Wallet Integration**: Users have a wallet (`WalletScreen.kt`) to manage funds and make payments on the Solana network (`SolanaRpc.kt`).
-   **Rewards System**: A `RewardsScreen.kt` implies a loyalty or rewards program.
-   **Settings**: A standard settings screen (`SettingsScreen.kt`).

## 5. Architecture

The application appears to follow a modern Android architecture, likely a variation of MVVM (Model-View-ViewModel) or MVI (Model-View-Intent), which is common for apps built with Jetpack Compose.

-   **View**: The Composable functions in the `ui/screen/` and `ui/components/` packages act as the View layer. They are responsible for rendering the UI based on state.
-   **ViewModel**: While no explicit `ViewModel` files are visible in the file tree, it is standard practice to have a ViewModel for each screen to hold state, handle business logic, and fetch data from the repository layer. These are likely located alongside the screens but not shown in the file listing.
-   **Model/Repository**: The `data/` package acts as the Repository layer, abstracting the data sources (Supabase and Mock data) from the rest of the app. The `model/` package defines the data structures.

## 6. Backend Integration

The app uses **Supabase** as its backend. `SupabaseClient.kt` likely initializes the Supabase client and provides methods for authentication, database operations (e.g., fetching listings, updating user profiles), and possibly storage. The presence of `MockBackend.kt` is a good practice, allowing the UI to be developed and tested independently of the live backend.

## 7. Blockchain Integration

A key feature is the integration with the **Solana** blockchain. The `wallet/` directory and `SolanaRpc.kt` file indicate that the app communicates directly with a Solana RPC node to perform wallet operations. This could include:

-   Checking wallet balances.
-   Creating and signing transactions.
-   Interacting with smart contracts for the escrow functionality.

This direct-to-blockchain approach gives users self-custody over their funds, which is a significant feature for a decentralized marketplace.
