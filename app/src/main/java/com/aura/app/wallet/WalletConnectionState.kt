package com.aura.app.wallet

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.funkatronics.encoders.Base58
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object WalletConnectionState {
    private const val TAG = "WalletConnection"
    private const val ASSOCIATION_TIMEOUT_MS = 60000L
    private const val CLIENT_TIMEOUT_MS = 90000L

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)

    /** Shown as overlay while waiting for wallet (Solflare, Phantom, etc.). Null when idle. */
    private val _walletPendingMessage = MutableStateFlow<String?>(null)
    val walletPendingMessage: StateFlow<String?> = _walletPendingMessage.asStateFlow()

    // Store a reference for starting the intent
    private var intentLauncher: ((Intent) -> Unit)? = null

    // Attempt to restore session on init
    fun init(launcher: (Intent) -> Unit) {
        intentLauncher = launcher
        val savedAddress = com.aura.app.data.AuraPreferences.walletAddress.value
        val savedToken = com.aura.app.data.AuraPreferences.getAuthToken()
        if (savedAddress != null && savedToken != null) {
            _walletAddress.value = savedAddress
            _authToken.value = savedToken
        }
    }

    fun connect(
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                if (_walletAddress.value == null || _authToken.value == null) {
                    _walletPendingMessage.value = "Opening wallet…"
                    val result = withContext(Dispatchers.IO) {
                        performAuthorization(null) // No existing token, perform full authorization
                    }
                    _walletAddress.value = result.first
                    _authToken.value = result.second
                    com.aura.app.data.AuraPreferences.setWalletInfo(result.first, result.second)
                    onSuccess(result.first)
                } else {
                    // Already connected, just report success with current address
                    _walletAddress.value?.let { onSuccess(it) }
                }
            } catch (e: ActivityNotFoundException) {
                onError(Exception("No MWA-compatible wallet found. Install Phantom or Solflare."))
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                onError(e)
            } finally {
                _walletPendingMessage.value = null
            }
        }
    }

    private suspend fun performAuthorization(token: String?): Pair<String, String> {
        val scenario = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null, // no specific wallet URI — use default
            scenario.port,
            scenario.session
        )

        // Launch wallet activity on the main thread
        withContext(Dispatchers.Main) {
            intentLauncher?.invoke(associationIntent)
                ?: throw IllegalStateException("WalletConnectionState not initialized. Call init() first.")
        }

        return try {
            // Wait for the wallet to connect
            val client = scenario.start()
                .get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                as MobileWalletAdapterClient

            try {
                // Try Reauthorize first if we have a token
                val authResult = if (token != null) {
                    try {
                        client.reauthorize(
                            Uri.parse("https://aura.app"),
                            Uri.parse("favicon.ico"),
                            "Aura",
                            token
                        ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    } catch (e: Exception) {
                        // Reauth failed, fallback to authorize
                        client.authorize(
                            Uri.parse("https://aura.app"),
                            Uri.parse("favicon.ico"),
                            "Aura",
                            "mainnet-beta"
                        ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    }
                } else {
                    client.authorize(
                        Uri.parse("https://aura.app"),
                        Uri.parse("favicon.ico"),
                        "Aura",
                        "mainnet-beta"
                    ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }

                val accounts = authResult.accounts
                if (accounts.isEmpty()) {
                    throw Exception("Wallet returned no accounts. Please try again.")
                }
                val pubKeyBytes = accounts[0].publicKey
                val address = Base58.encodeToString(pubKeyBytes)
                val newToken = authResult.authToken

                Pair(address, newToken ?: "")
            } finally {
                scenario.close()
                    .get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        } catch (e: ExecutionException) {
            scenario.close()
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            scenario.close()
            throw Exception("Wallet connection timed out. Please try again.")
        }
    }

    suspend fun signAndSendTransaction(
        scope: CoroutineScope,
        base64EncodedTx: String,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                _walletPendingMessage.value = "Please approve in your wallet"
                val txSignature = withContext(Dispatchers.IO) {
                    performTransaction(base64EncodedTx)
                }
                onSuccess(txSignature)
            } catch (e: Exception) {
                Log.e(TAG, "Transaction failed", e)
                onError(e)
            } finally {
                _walletPendingMessage.value = null
            }
        }
    }

    private suspend fun performTransaction(
        base64EncodedTx: String
    ): String {
        val scenario = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)

        val associationIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            scenario.port,
            scenario.session
        )

        withContext(Dispatchers.Main) {
            intentLauncher?.invoke(associationIntent)
                ?: throw IllegalStateException("WalletConnectionState not initialized. Call init() first.")
        }

        return try {
            val client = scenario.start()
                .get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                as MobileWalletAdapterClient

            try {
                // Re-authorize with stored token if available; fallback to full authorize on failure
                val token = _authToken.value
                val authResult = if (token != null) {
                    try {
                        client.reauthorize(
                            Uri.parse("https://aura.app"),
                            Uri.parse("favicon.ico"),
                            "Aura",
                            token
                        ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    } catch (e: Exception) {
                        Log.w(TAG, "Reauthorize failed, falling back to authorize: ${e.message}")
                        _authToken.value = null
                        client.authorize(
                            Uri.parse("https://aura.app"),
                            Uri.parse("favicon.ico"),
                            "Aura",
                            "mainnet-beta"
                        ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    }
                } else {
                    client.authorize(
                        Uri.parse("https://aura.app"),
                        Uri.parse("favicon.ico"),
                        "Aura",
                        "mainnet-beta"
                    ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }
                // Update stored token if we got a new one from full authorize
                authResult.authToken?.let { newToken ->
                    _authToken.value = newToken
                    com.aura.app.data.AuraPreferences.setWalletInfo(_walletAddress.value, newToken)
                }

                // Decode the serialized transaction (full Transaction format: signatures + message)
                val txPayload = android.util.Base64.decode(base64EncodedTx, android.util.Base64.NO_WRAP)
                
                val result = client.signAndSendTransactions(
                    arrayOf(txPayload),
                    null // minContextSlot
                )
                
                // For Kotlin MWA bindings, result may be wrapped in Execution or deferred
                val resolvedResult = result.get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                val signatures = resolvedResult.signatures
                if (signatures.isNullOrEmpty()) {
                    throw Exception("Wallet returned no transaction signature.")
                }
                val signatureBytes = signatures[0]
                return Base58.encodeToString(signatureBytes)
            } finally {
                scenario.close()
                    .get(ASSOCIATION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        } catch (e: ExecutionException) {
            scenario.close()
            throw e.cause ?: e
        } catch (e: TimeoutException) {
            scenario.close()
            throw Exception("Transaction timed out. Please try again.")
        }
    }

    fun disconnect() {
        _walletAddress.value = null
        _authToken.value = null
        com.aura.app.data.AuraPreferences.setWalletInfo(null, null)
    }
}
