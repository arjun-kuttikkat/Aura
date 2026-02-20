package com.aura.app.wallet

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.RpcCluster
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object WalletConnectionState {

    private const val TAG = "WalletConnectionState"
    private const val PREFS_NAME = "aura_wallet_prefs"
    private const val KEY_WALLET = "wallet_address"
    private const val KEY_AUTH = "auth_token"

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)

    private lateinit var sender: ActivityResultSender
    private lateinit var prefs: SharedPreferences

    private val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://aura.app"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Aura"
        )
    )

    fun init(activityResultSender: ActivityResultSender, context: Context) {
        sender = activityResultSender
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context, PREFS_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        // Restore persisted session
        val savedWallet = prefs.getString(KEY_WALLET, null)
        val savedAuth = prefs.getString(KEY_AUTH, null)
        if (savedWallet != null) {
            _walletAddress.value = savedWallet
            _authToken.value = savedAuth
            Log.d(TAG, "Restored wallet session: ${savedWallet.take(8)}…")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Connect
    // ─────────────────────────────────────────────────────────────────────────

    fun connect(
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            @Suppress("DEPRECATION")
            val result = walletAdapter.transact(sender) {
                authorize(
                    Uri.parse("https://aura.app"),
                    Uri.parse("favicon.ico"),
                    "Aura",
                    RpcCluster.MainnetBeta
                )
            }
            when (result) {
                is TransactionResult.Success -> {
                    val pubKeyBytes = result.authResult.accounts.first().publicKey
                    val address = Base58.encodeToString(pubKeyBytes)
                    _authToken.value = result.authResult.authToken
                    _walletAddress.value = address
                    // Persist session
                    prefs.edit()
                        .putString(KEY_WALLET, address)
                        .putString(KEY_AUTH, result.authResult.authToken)
                        .apply()
                    onSuccess(address)
                }
                is TransactionResult.NoWalletFound ->
                    onError(Exception("No MWA-compatible wallet found. Install Phantom or Solflare."))
                is TransactionResult.Failure -> {
                    Log.e(TAG, "Connection failed", result.e)
                    onError(result.e)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sign & Send a SOL Transfer
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Signs and sends a SOL transfer:
     *   feePayer/sender = connected wallet
     *   recipient       = [recipientAddress] (the listing's sellerWallet)
     *   amount          = [amountSol] converted to lamports
     *
     * Transaction is built manually in Solana legacy wire format so we depend
     * only on libraries already in the project.
     */
    suspend fun signAndSendTransaction(
        scope: CoroutineScope,
        recipientAddress: String,
        amountSol: Double,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                // 1. Fetch blockhash
                val blockhash = SolanaRpc.getLatestBlockhash()
                if (blockhash == null) {
                    onError(Exception("Failed to fetch blockhash. Check your RPC endpoint."))
                    return@launch
                }

                // 2. Get connected wallet public key bytes
                val fromPubkeyB58 = _walletAddress.value
                    ?: run { onError(Exception("Wallet not connected")); return@launch }

                val fromPubkeyBytes = runCatching { Base58.decode(fromPubkeyB58) }.getOrElse {
                    onError(Exception("Invalid sender address: ${it.message}")); return@launch
                }
                val toPubkeyBytes = runCatching { Base58.decode(recipientAddress) }.getOrElse {
                    onError(Exception("Invalid recipient address '$recipientAddress': ${it.message}")); return@launch
                }
                val blockhashBytes = runCatching { Base58.decode(blockhash) }.getOrElse {
                    onError(Exception("Invalid blockhash: ${it.message}")); return@launch
                }

                // Solana public keys must be exactly 32 bytes
                if (fromPubkeyBytes.size != 32) {
                    onError(Exception("Sender key wrong length (${fromPubkeyBytes.size} bytes)")); return@launch
                }
                if (toPubkeyBytes.size != 32) {
                    onError(Exception("Recipient key wrong length (${toPubkeyBytes.size} bytes)")); return@launch
                }

                val lamports = (amountSol * 1_000_000_000L).toLong()

                // 3. Serialize the transaction bytes
                val txBytes = buildTransferTransaction(
                    fromPubkeyBytes = fromPubkeyBytes,
                    toPubkeyBytes = toPubkeyBytes,
                    lamports = lamports,
                    recentBlockhashBytes = blockhashBytes
                )

                // 4. Pass to MWA for signing + broadcasting
                val result = walletAdapter.transact(sender) {
                    @Suppress("DEPRECATION")
                    reauthorize(
                        Uri.parse("https://aura.app"),
                        Uri.parse("favicon.ico"),
                        "Aura",
                        _authToken.value ?: ""
                    )
                    signAndSendTransactions(arrayOf(txBytes))
                }

                when (result) {
                    is TransactionResult.Success -> {
                        // The first signature bytes from the wallet response
                        val sigBytes = result.authResult.accounts.first().publicKey
                        val sigB58 = Base58.encodeToString(sigBytes)
                        Log.i(TAG, "Transaction sent. Sig: $sigB58")
                        onSuccess(sigB58)
                    }
                    is TransactionResult.NoWalletFound ->
                        onError(Exception("No wallet found"))
                    is TransactionResult.Failure -> {
                        Log.e(TAG, "Transaction failed", result.e)
                        onError(result.e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error in signAndSendTransaction", e)
                onError(e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Disconnect
    // ─────────────────────────────────────────────────────────────────────────

    fun disconnect() {
        _walletAddress.value = null
        _authToken.value = null
        prefs.edit().clear().apply()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Solana Legacy Transaction Serializer (SystemProgram.transfer)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a Solana legacy transaction for a SOL transfer.
     * Wire format:
     *   [compact_u16: num_signatures=1]
     *   [64 bytes: signature placeholder (zeroes) — wallet will fill this in]
     *   [MessageHeader: 3 bytes]
     *   [compact_u16: num_account_keys=3]
     *   [32 bytes × 3: fromPubkey, toPubkey, SystemProgram]
     *   [32 bytes: recentBlockhash]
     *   [compact_u16: num_instructions=1]
     *   [Instruction: programIdIndex=2, accountIndices=[0,1], data=transfer(lamports)]
     */
    private fun buildTransferTransaction(
        fromPubkeyBytes: ByteArray,
        toPubkeyBytes: ByteArray,
        lamports: Long,
        recentBlockhashBytes: ByteArray
    ): ByteArray {
        require(fromPubkeyBytes.size == 32) { "from pubkey must be 32 bytes" }
        require(toPubkeyBytes.size == 32) { "to pubkey must be 32 bytes" }
        require(recentBlockhashBytes.size == 32) { "blockhash must be 32 bytes" }

        val systemProgram = ByteArray(32) // All zeros = System Program ID

        // Instruction data: [type=2 as 4-byte LE][lamports as 8-byte LE]
        val instructionData = ByteArray(12).also { d ->
            d[0] = 2; d[1] = 0; d[2] = 0; d[3] = 0 // transfer instruction index
            for (i in 0..7) d[4 + i] = ((lamports ushr (i * 8)) and 0xFF).toByte()
        }

        val message = buildList<Byte> {
            // Header
            add(1.toByte()) // numRequiredSignatures
            add(0.toByte()) // numReadonlySignedAccounts
            add(1.toByte()) // numReadonlyUnsignedAccounts (System Program)

            // Account keys (compact length prefix = 3)
            add(3.toByte())
            addAll(fromPubkeyBytes.toList())
            addAll(toPubkeyBytes.toList())
            addAll(systemProgram.toList())

            // Recent blockhash
            addAll(recentBlockhashBytes.toList())

            // Instructions (compact count = 1)
            add(1.toByte())
            add(2.toByte()) // programIdIndex → index 2 = System Program
            add(2.toByte()) // 2 account indices
            add(0.toByte()) // accounts[0] = fromPubkey (sender)
            add(1.toByte()) // accounts[1] = toPubkey (receiver)
            add(instructionData.size.toByte()) // instruction data length
            addAll(instructionData.toList())
        }

        return buildList<Byte> {
            add(1.toByte())              // compact_u16: 1 signature
            addAll(ByteArray(64).toList()) // 64-byte signature placeholder
            addAll(message)
        }.toByteArray()
    }
}
