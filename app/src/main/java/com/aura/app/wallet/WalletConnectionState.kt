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
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.functions.functions
import io.ktor.client.plugins.timeout
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object WalletConnectionState {
    private const val TAG = "WalletConnection"
    private const val ASSOCIATION_TIMEOUT_MS = 60000L
    private const val CLIENT_TIMEOUT_MS = 90000L
    private const val WALLET_AUTH_RETRIES = 3

    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)

    /** Shown as overlay while waiting for wallet (Solflare, Phantom, etc.). Null when idle. */
    private val _walletPendingMessage = MutableStateFlow<String?>(null)
    val walletPendingMessage: StateFlow<String?> = _walletPendingMessage.asStateFlow()

    // Supabase JWT obtained from wallet-auth Edge Function
    private val _supabaseJwt = MutableStateFlow<String?>(null)
    val supabaseJwt: StateFlow<String?> = _supabaseJwt.asStateFlow()

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
                    // Step 1: Fetch nonce from wallet-auth Edge Function
                    val address: String
                    val mwaToken: String
                    val signedNonce: ByteArray

                    val nonceResult = withContext(Dispatchers.IO) { fetchAuthNonce() }

                    // Step 2: Perform MWA authorization and sign nonce in one session
                    val result = withContext(Dispatchers.IO) {
                        performAuthorizationWithNonce(null, nonceResult.first, nonceResult.second)
                    }
                    address = result.address
                    mwaToken = result.mwaToken
                    signedNonce = result.signedNonce

                    _walletAddress.value = address
                    _authToken.value = mwaToken
                    com.aura.app.data.AuraPreferences.setWalletInfo(address, mwaToken)

                    // Step 3: Exchange signature for Supabase JWT
                    withContext(Dispatchers.IO) {
                        exchangeSignatureForJwt(address, signedNonce)
                    }

                    onSuccess(address)
                } else {
                    // Already connected — refresh JWT if missing
                    if (_supabaseJwt.value == null) {
                        val savedJwt = com.aura.app.data.AuraPreferences.getSupabaseJwt()
                        if (savedJwt != null) {
                            _supabaseJwt.value = savedJwt
                            applyJwtToSupabase(savedJwt)
                        }
                    }
                    _walletAddress.value?.let { onSuccess(it) }
                }
            } catch (e: ActivityNotFoundException) {
                onError(Exception("No MWA-compatible wallet found. Install Phantom or Solflare."))
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                onError(Exception(toUserFriendlyMessage(e), e))
            } finally {
                _walletPendingMessage.value = null
            }
        }
    }

    private fun toUserFriendlyMessage(e: Exception): String {
        val msg = e.message?.lowercase() ?: ""
        return when {
            msg.contains("socket timeout") || msg.contains("request timeout") || msg.contains("timeout") -> "Connection timed out. Check your network and try again."
            msg.contains("unreachable") || msg.contains("connection refused") || msg.contains("network") -> "No internet connection. Check your network and try again."
            msg.contains("failed with message") -> "Server connection failed. Please try again."
            else -> e.message ?: "Connection failed. Please try again."
        }
    }

    /** Fetch a one-time nonce from the wallet-auth Edge Function. Returns (walletAddress_placeholder, nonce). */
    private suspend fun fetchAuthNonce(): Pair<String, String> {
        // We don't know the wallet address yet, so we'll use a temp placeholder
        // The nonce endpoint doesn't need the wallet address upfront for new users
        // We'll request the nonce after we have the wallet address from MWA
        // For now, return empty - we'll fetch the nonce during the MWA session
        return Pair("", "")
    }

    data class AuthResult(val address: String, val mwaToken: String, val signedNonce: ByteArray)

    private suspend fun performAuthorizationWithNonce(
        token: String?,
        @Suppress("UNUSED_PARAMETER") placeholder: String,
        @Suppress("UNUSED_PARAMETER") nonce: String,
    ): AuthResult {
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
                val authResult = if (token != null) {
                    try {
                        client.reauthorize(
                            Uri.parse("https://aura.app"),
                            Uri.parse("favicon.ico"),
                            "Aura",
                            token
                        ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    } catch (e: Exception) {
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

                // Now fetch nonce using the real wallet address
                val actualNonce = fetchNonceForWallet(address)

                // Sign the nonce message with the wallet
                val nonceMessage = "Aura wallet-auth nonce: $actualNonce".toByteArray(Charsets.UTF_8)
                val signResult = client.signMessages(
                    arrayOf(nonceMessage),
                    arrayOf(pubKeyBytes)
                ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                val signedNonce = signResult.signedPayloads?.firstOrNull()
                    ?: throw Exception("Wallet did not return a signed message.")

                AuthResult(address, newToken ?: "", signedNonce)
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

    /** Request a nonce from wallet-auth Edge Function. */
    private suspend fun fetchNonceForWallet(walletAddress: String): String {
        return withRetry(WALLET_AUTH_RETRIES) {
            val db = com.aura.app.data.SupabaseClient.client
            val reqBody = buildJsonObject {
                put("action", "nonce")
                put("walletAddress", walletAddress)
            }
            val response = db.functions.invoke("wallet-auth") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
            }
            val text = response.bodyAsText()
            val json = Json.parseToJsonElement(text).jsonObject
            val error = json["error"]?.jsonPrimitive?.content
            if (!error.isNullOrBlank()) throw Exception("wallet-auth nonce failed: $error")
            json["nonce"]?.jsonPrimitive?.content
                ?: throw Exception("No nonce returned from wallet-auth")
        }
    }

    private suspend fun <T> withRetry(maxAttempts: Int, block: suspend () -> T): T {
        var lastEx: Exception? = null
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastEx = e
                if (attempt < maxAttempts - 1) {
                    val delayMs = 1000L * (1 shl attempt)
                    Log.w(TAG, "wallet-auth attempt ${attempt + 1}/$maxAttempts failed, retrying in ${delayMs}ms: ${e.message}")
                    delay(delayMs)
                } else {
                    throw e
                }
            }
        }
        throw lastEx ?: Exception("Retry exhausted")
    }

    /** Exchange signed nonce for a Supabase JWT. */
    private suspend fun exchangeSignatureForJwt(walletAddress: String, signedNonce: ByteArray) {
        withRetry(WALLET_AUTH_RETRIES) {
            val db = com.aura.app.data.SupabaseClient.client
            val signatureBase64 = android.util.Base64.encodeToString(signedNonce, android.util.Base64.NO_WRAP)
            val reqBody = buildJsonObject {
                put("action", "verify")
                put("walletAddress", walletAddress)
                put("signatureBase64", signatureBase64)
            }
            val response = db.functions.invoke("wallet-auth") {
                setBody(reqBody.toString())
                contentType(ContentType.Application.Json)
                timeout {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 60_000
                }
            }
            val text = response.bodyAsText()
            val json = Json.parseToJsonElement(text).jsonObject
            val error = json["error"]?.jsonPrimitive?.content
            if (!error.isNullOrBlank()) {
                Log.w(TAG, "wallet-auth verify failed: $error (non-fatal, continuing with anon)")
                return@withRetry
            }
            val jwt = json["token"]?.jsonPrimitive?.content ?: return@withRetry
            _supabaseJwt.value = jwt
            com.aura.app.data.AuraPreferences.setSupabaseJwt(jwt)
            applyJwtToSupabase(jwt)
            Log.i(TAG, "Wallet-auth: Supabase JWT acquired for $walletAddress")
        }
    }

    /** Apply JWT to the Supabase client for authenticated RLS access. */
    private fun applyJwtToSupabase(jwt: String) {
        try {
            val supabase = com.aura.app.data.SupabaseClient.client
            // Use Auth.importAuthToken for custom JWTs
            kotlinx.coroutines.runBlocking {
                supabase.pluginManager.getPlugin(Auth).importAuthToken(jwt)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to import JWT into Supabase (non-fatal): ${e.message}")
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
                onError(Exception(toUserFriendlyMessage(e), e))
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
        _supabaseJwt.value = null
        com.aura.app.data.AuraPreferences.setWalletInfo(null, null)
        com.aura.app.data.AuraPreferences.setSupabaseJwt(null)
    }
}
