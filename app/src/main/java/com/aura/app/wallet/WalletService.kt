package com.aura.app.wallet

/**
 * Mobile Wallet Adapter (MWA) skeleton for Solana Mobile.
 * Provides placeholder implementations that can later be wired to a real wallet
 * via [com.solanamobile:mobile-wallet-adapter-clientlib-ktx].
 */
class WalletService {

    /**
     * Connects to the wallet and returns the base58-encoded public key.
     * TODO: Wire to MWA authorize() session.
     */
    suspend fun connect(): Result<String> = runCatching {
        // Placeholder: return fake base58 public key for UI testing
        "PLACEHOLDER_PUBKEY_BASE58_11111111111111111111"
    }

    /**
     * Signs a message with the connected wallet's keypair.
     * TODO: Wire to MWA signMessages().
     */
    suspend fun signMessage(message: ByteArray): Result<ByteArray> = runCatching {
        // Placeholder: echo back a deterministic fake signature
        ByteArray(64) { if (it < message.size) message[it] else 0 }
    }

    /**
     * Signs and sends a transaction, returns the signature string.
     * TODO: Wire to MWA signAndSendTransactions().
     */
    suspend fun signAndSendTransaction(txBytes: ByteArray): Result<String> = runCatching {
        // Placeholder: return fake signature for UI testing
        "PLACEHOLDER_SIG_${txBytes.take(4).joinToString("") { "%02x".format(it) }}"
    }
}
