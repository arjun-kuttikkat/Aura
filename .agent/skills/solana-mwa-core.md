---
name: Solana Mobile Wallet Adapter & SGT Checking
description: Instructions for integrating Mobile Wallet Adapter (MWA) in an Android dApp, handling authorizations, and querying for the Seeker Genesis Token (SGT).
---

# Solana MWA & SGT Verification

## 1. MWA Dependencies
Always include the following in your Compose/Android project:
```kotlin
// Example dependencies
// implementation("com.solana:mobile-wallet-adapter-clientlib-ktx:2.0.0") 
```

## 2. Authorization Flow
Use the `MobileWalletAdapter` to connect to a wallet.
- Scenario: User clicks "Connect Wallet"
- Action: Request authorization via `MobileWalletAdapter().transact(sender) {...}`.
- Handle `AuthorizationResult`.
- Store the `authToken`, `publicKey`, and `walletUriBase` securely for future sessions.

## 3. SGT Verification (Token Gating)
After successful authorization, verify if the wallet holds the Seeker Genesis Token (SGT).
- Obtain the SGT mint address.
- Use a Kotlin Solana RPC client to query `getTokenAccountsByOwner` or `getParsedTokenAccountsByOwner`.
- If the balance > 0, grant "Verified" level access.
- Otherwise, restrict features.
