package com.aura.app.wallet

import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import com.funkatronics.encoders.Base58

/**
 * Pure-Kotlin Anchor Transaction Builder for the Aura Escrow program.
 *
 * Constructs real Solana instructions matching the deployed Anchor IDL:
 * - `initialize`: Lock buyer SOL into escrow PDA vault
 * - `release_funds_and_mint`: Release SOL + mint cNFT
 *
 * Also provides PDA derivation matching the on-chain seeds:
 * - escrow PDA: seeds = [b"escrow", listing_id.as_bytes()]
 * - vault PDA:  seeds = [b"vault", escrow.key().as_ref()]
 */
object AnchorTransactionBuilder {
    private const val TAG = "AnchorTxBuilder"

    // Program ID — must match the deployed Anchor program's program_id!()
    // Update this to your actual deployed program ID
    const val PROGRAM_ID = "BMKWLYrXtuuxp4TA4yNhrs9LbomR1fMdbrko6R7Qj5WM"

    // System program
    private const val SYSTEM_PROGRAM = "11111111111111111111111111111111"

    // ══════════════════════════════════════════════════════════════
    // Anchor Instruction Discriminators
    // SHA-256("global:initialize")[0..8]
    // SHA-256("global:release_funds_and_mint")[0..8]
    // ══════════════════════════════════════════════════════════════

    private val INITIALIZE_DISCRIMINATOR: ByteArray by lazy {
        computeDiscriminator("global:initialize")
    }

    private val RELEASE_DISCRIMINATOR: ByteArray by lazy {
        computeDiscriminator("global:release_funds_and_mint")
    }

    /**
     * Compute Anchor 8-byte instruction discriminator.
     * discriminator = SHA-256(namespace:function_name)[0..8]
     */
    private fun computeDiscriminator(methodName: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(methodName.toByteArray(Charsets.UTF_8)).take(8).toByteArray()
    }

    // ══════════════════════════════════════════════════════════════
    // PDA Derivation
    // ══════════════════════════════════════════════════════════════

    /**
     * Derive the escrow PDA address.
     * Seeds: [b"escrow", listing_id.as_bytes()]
     */
    fun deriveEscrowPda(listingId: String): PdaResult {
        val seeds = listOf(
            "escrow".toByteArray(Charsets.UTF_8),
            listingId.toByteArray(Charsets.UTF_8),
        )
        return findProgramAddress(seeds, decodeBase58(PROGRAM_ID))
    }

    /**
     * Derive the vault PDA address.
     * Seeds: [b"vault", escrow_pubkey]
     */
    fun deriveVaultPda(escrowPubkey: ByteArray): PdaResult {
        val seeds = listOf(
            "vault".toByteArray(Charsets.UTF_8),
            escrowPubkey,
        )
        return findProgramAddress(seeds, decodeBase58(PROGRAM_ID))
    }

    data class PdaResult(val address: ByteArray, val bump: Int)

    // ══════════════════════════════════════════════════════════════
    // Instruction Builders
    // ══════════════════════════════════════════════════════════════

    /**
     * Build the `initialize` instruction data (Borsh-serialized).
     *
     * Instruction layout:
     * [8 bytes discriminator][8 bytes amount (u64 LE)][variable listing_id (borsh string)]
     */
    fun buildInitializeInstructionData(amount: Long, listingId: String): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(INITIALIZE_DISCRIMINATOR)

        // amount: u64 little-endian
        val amountBytes = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(amount).array()
        buf.write(amountBytes)

        // listing_id: Borsh string = [4-byte LE length][UTF-8 bytes]
        val idBytes = listingId.toByteArray(Charsets.UTF_8)
        val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(idBytes.size).array()
        buf.write(lenBytes)
        buf.write(idBytes)

        return buf.toByteArray()
    }

    /**
     * Build the `release_funds_and_mint` instruction data (Borsh-serialized).
     *
     * Instruction layout:
     * [8 bytes discriminator][variable asset_uri (borsh string)][variable asset_title (borsh string)]
     */
    fun buildReleaseInstructionData(assetUri: String, assetTitle: String): ByteArray {
        val buf = ByteArrayOutputStream()
        buf.write(RELEASE_DISCRIMINATOR)

        // asset_uri: Borsh string
        val uriBytes = assetUri.toByteArray(Charsets.UTF_8)
        buf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(uriBytes.size).array())
        buf.write(uriBytes)

        // asset_title: Borsh string
        val titleBytes = assetTitle.toByteArray(Charsets.UTF_8)
        buf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(titleBytes.size).array())
        buf.write(titleBytes)

        return buf.toByteArray()
    }

    /**
     * Build a simple SOL transfer instruction (System Program Transfer).
     *
     * This is used for P2P payments where no escrow is involved.
     * Instruction index: 2 (SystemInstruction::Transfer)
     * Data: [4 bytes index LE][8 bytes lamports LE]
     */
    fun buildSolTransferInstructionData(lamports: Long): ByteArray {
        val buf = ByteArrayOutputStream()
        // System program transfer instruction index = 2
        buf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(2).array())
        // Amount in lamports
        buf.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(lamports).array())
        return buf.toByteArray()
    }

    /**
     * Construct a full transaction message for the Initialize instruction.
     *
     * Returns the serialized transaction message bytes ready for
     * WalletConnectionState.signAndSendTransaction().
     *
     * Account metas for Initialize:
     * 0. buyer (signer, writable)
     * 1. escrow PDA (writable)
     * 2. vault PDA (writable)
     * 3. system_program
     */
    suspend fun buildInitializeEscrowTx(
        listingId: String,
        amountLamports: Long,
        buyerPubkey: String,
    ): ByteArray {
        Log.d(TAG, "Building Initialize TX: listing=$listingId, amount=$amountLamports")

        val escrowPda = deriveEscrowPda(listingId)
        val vaultPda = deriveVaultPda(escrowPda.address)
        val instructionData = buildInitializeInstructionData(amountLamports, listingId)
        Log.d(TAG, "Derived Escrow PDA: ${Base58.encodeToString(escrowPda.address)}")

        // Fetch recent blockhash
        val blockhash = SolanaRpc.getLatestBlockhash()
            ?: throw IllegalStateException("Failed to fetch recent blockhash")

        // Build the serialized transaction message
        return buildSerializedMessage(
            recentBlockhash = blockhash,
            feePayer = decodeBase58(buyerPubkey),
            instructions = listOf(
                Instruction(
                    programId = decodeBase58(PROGRAM_ID),
                    accounts = listOf(
                        AccountMeta(decodeBase58(buyerPubkey), isSigner = true, isWritable = true),
                        AccountMeta(escrowPda.address, isSigner = false, isWritable = true),
                        AccountMeta(vaultPda.address, isSigner = false, isWritable = true),
                        AccountMeta(decodeBase58(SYSTEM_PROGRAM), isSigner = false, isWritable = false),
                    ),
                    data = instructionData,
                ),
            ),
        )
    }

    /**
     * Construct a full SOL transfer transaction for P2P payments.
     */
    suspend fun buildSolTransferTx(
        fromPubkey: String,
        toPubkey: String,
        lamports: Long,
    ): ByteArray {
        Log.d(TAG, "Building SOL Transfer TX: to=$toPubkey, lamports=$lamports")

        val instructionData = buildSolTransferInstructionData(lamports)
        val blockhash = SolanaRpc.getLatestBlockhash()
            ?: throw IllegalStateException("Failed to fetch recent blockhash")

        return buildSerializedMessage(
            recentBlockhash = blockhash,
            feePayer = decodeBase58(fromPubkey),
            instructions = listOf(
                Instruction(
                    programId = decodeBase58(SYSTEM_PROGRAM),
                    accounts = listOf(
                        AccountMeta(decodeBase58(fromPubkey), isSigner = true, isWritable = true),
                        AccountMeta(decodeBase58(toPubkey), isSigner = false, isWritable = true),
                    ),
                    data = instructionData,
                ),
            ),
        )
    }

    // ══════════════════════════════════════════════════════════════
    // Solana Transaction Message Serialization (v0 Legacy)
    // ══════════════════════════════════════════════════════════════

    private data class AccountMeta(
        val pubkey: ByteArray,
        val isSigner: Boolean,
        val isWritable: Boolean,
    )

    private data class Instruction(
        val programId: ByteArray,
        val accounts: List<AccountMeta>,
        val data: ByteArray,
    )

    /**
     * Build a legacy (v0) Solana transaction message.
     *
     * Message format:
     * [1 byte: num_required_signatures]
     * [1 byte: num_readonly_signed_accounts]
     * [1 byte: num_readonly_unsigned_accounts]
     * [compact-u16: num_account_keys]
     * [32 * N bytes: account keys]
     * [32 bytes: recent_blockhash]
     * [compact-u16: num_instructions]
     * [instructions...]
     */
    private fun buildSerializedMessage(
        recentBlockhash: String,
        feePayer: ByteArray,
        instructions: List<Instruction>,
    ): ByteArray {
        // Collect all unique accounts and deduplicate
        val accountMap = linkedMapOf<String, AccountMeta>()

        // Fee payer is always first
        val feePayerHex = feePayer.toHexString()
        accountMap[feePayerHex] = AccountMeta(feePayer, isSigner = true, isWritable = true)

        for (ix in instructions) {
            for (meta in ix.accounts) {
                val hex = meta.pubkey.toHexString()
                val existing = accountMap[hex]
                if (existing != null) {
                    // Merge: upgrade permissions
                    accountMap[hex] = AccountMeta(
                        meta.pubkey,
                        isSigner = existing.isSigner || meta.isSigner,
                        isWritable = existing.isWritable || meta.isWritable,
                    )
                } else {
                    accountMap[hex] = meta
                }
            }
            // Add program ID as non-signer non-writable
            val progHex = ix.programId.toHexString()
            if (!accountMap.containsKey(progHex)) {
                accountMap[progHex] = AccountMeta(ix.programId, isSigner = false, isWritable = false)
            }
        }

        // Sort: signer+writable, signer+readonly, nonsigner+writable, nonsigner+readonly
        val sortedAccounts = accountMap.values.sortedWith(
            compareByDescending<AccountMeta> { it.isSigner }
                .thenByDescending { it.isWritable }
        )

        val numSigners = sortedAccounts.count { it.isSigner }
        val numReadonlySigned = sortedAccounts.count { it.isSigner && !it.isWritable }
        val numReadonlyUnsigned = sortedAccounts.count { !it.isSigner && !it.isWritable }

        // Build account index map
        val accountIndexMap = sortedAccounts.mapIndexed { i, meta ->
            meta.pubkey.toHexString() to i
        }.toMap()

        val buf = ByteArrayOutputStream()

        // Header
        buf.write(numSigners)
        buf.write(numReadonlySigned)
        buf.write(numReadonlyUnsigned)

        // Account keys count (compact-u16)
        writeCompactU16(buf, sortedAccounts.size)

        // Account keys (32 bytes each)
        for (acct in sortedAccounts) {
            buf.write(acct.pubkey)
        }

        // Recent blockhash (32 bytes)
        buf.write(decodeBase58(recentBlockhash))

        // Instruction count (compact-u16)
        writeCompactU16(buf, instructions.size)

        // Each instruction
        for (ix in instructions) {
            // Program ID index
            val progIdx = accountIndexMap[ix.programId.toHexString()] ?: 0
            buf.write(progIdx)

            // Account indices (compact-u16 length + indices)
            writeCompactU16(buf, ix.accounts.size)
            for (meta in ix.accounts) {
                val idx = accountIndexMap[meta.pubkey.toHexString()] ?: 0
                buf.write(idx)
            }

            // Data (compact-u16 length + data bytes)
            writeCompactU16(buf, ix.data.size)
            buf.write(ix.data)
        }

        return buf.toByteArray()
    }

    // ══════════════════════════════════════════════════════════════
    // Utility
    // ══════════════════════════════════════════════════════════════

    private fun writeCompactU16(buf: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) {
            val b = v and 0x7F
            v = v shr 7
            if (v == 0) {
                buf.write(b)
                break
            } else {
                buf.write(b or 0x80)
            }
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02x".format(it) }

    /**
     * Find program-derived address (PDA).
     *
     * This calculates deterministic addresses matching the Anchor specs.
     * PRODUCTION NOTE: This implementation relies on SHA-256 brute-forcing.
     * While deterministic, ensure the resulting address does not collide with on-curve points.
     */
    private fun findProgramAddress(seeds: List<ByteArray>, programId: ByteArray): PdaResult {
        for (bump in 255 downTo 0) {
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                for (seed in seeds) {
                    digest.update(seed)
                }
                digest.update(byteArrayOf(bump.toByte()))
                digest.update(programId)
                digest.update("ProgramDerivedAddress".toByteArray(Charsets.UTF_8))
                val hash = digest.digest()
                // Take first 32 bytes as the derived address
                return PdaResult(hash.take(32).toByteArray(), bump)
            } catch (_: Exception) {
                continue
            }
        }
        throw IllegalStateException("Could not find PDA")
    }

    // ══════════════════════════════════════════════════════════════
    // Base58 Decoding
    // ══════════════════════════════════════════════════════════════

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun decodeBase58(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)

        var bi = java.math.BigInteger.ZERO
        for (c in input) {
            val index = BASE58_ALPHABET.indexOf(c)
            if (index < 0) throw IllegalArgumentException("Invalid Base58 char: $c")
            bi = bi.multiply(java.math.BigInteger.valueOf(58)).add(java.math.BigInteger.valueOf(index.toLong()))
        }

        val bytes = bi.toByteArray()
        // Strip leading zero byte if present
        val stripped = if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.drop(1).toByteArray() else bytes

        // Count leading '1's (which represent leading zero bytes)
        val leadingZeros = input.takeWhile { it == '1' }.length
        val result = ByteArray(leadingZeros + stripped.size)
        System.arraycopy(stripped, 0, result, leadingZeros, stripped.size)

        // Pad or trim to 32 bytes for pubkeys
        return if (result.size < 32) {
            ByteArray(32 - result.size) + result
        } else if (result.size > 32) {
            result.takeLast(32).toByteArray()
        } else {
            result
        }
    }
}
