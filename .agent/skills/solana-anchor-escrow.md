---
name: Solana Stand-Off Escrow Smart Contract Integration
description: Instructions on how to interact with a custom Anchor program for the Stand-Off Escrow feature.
---

# Stand-Off Escrow Integration

## 1. Escrow Logic
The escrow program holds funds (SOL/USDC) and the asset (NFT) until spatial separation is verified.

## 2. PDA Derivation
Calculate the Escrow PDA in Kotlin. Ensure you match the seed logic from the Anchor Rust program.
```kotlin
// Kotlin snippet for PDA derivation example
/*
val (escrowPda, bump) = PublicKey.findProgramAddress(
    listOf(
        "escrow".toByteArray(),
        sellerPubkey.toByteArray(),
        buyerPubkey.toByteArray()
    ),
    programId
)
*/
```

## 3. Transaction Building
Build instructions to initialize, fund, and release the escrow.
- Construct custom `TransactionInstruction` objects referencing the Anchor discriminator.
- Ensure the latest blockhash is fetched from the RPC.
- Sign the transaction via MWA's `signAndSendTransactions`.
