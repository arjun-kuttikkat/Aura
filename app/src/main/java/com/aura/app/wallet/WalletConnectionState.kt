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

    // Store a reference for starting the intent
    private var intentLauncher: ((Intent) -> Unit)? = null

    fun init(launcher: (Intent) -> Unit) {
        intentLauncher = launcher
    }

    fun connect(
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    performAuthorization()
                }
                _walletAddress.value = result.first
                _authToken.value = result.second
                onSuccess(result.first)
            } catch (e: ActivityNotFoundException) {
                onError(Exception("No MWA-compatible wallet found. Install Phantom or Solflare."))
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                onError(e)
            }
        }
    }

    private suspend fun performAuthorization(): Pair<String, String> {
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
                // Authorize
                val authResult = client.authorize(
                    Uri.parse("https://aura.app"),    // identityUri
                    Uri.parse("favicon.ico"),          // iconUri
                    "Aura",                             // identityName
                    "mainnet-beta"                      // cluster
                ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)

                val pubKeyBytes = authResult.accounts[0].publicKey
                val address = Base58.encodeToString(pubKeyBytes)
                val token = authResult.authToken

                Pair(address, token)
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
        recipientAddress: String,
        amountSol: Double,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            try {
                val txSignature = withContext(Dispatchers.IO) {
                    performTransaction(recipientAddress, amountSol)
                }
                onSuccess(txSignature)
            } catch (e: Exception) {
                Log.e(TAG, "Transaction failed", e)
                onError(e)
            }
        }
    }

    private suspend fun performTransaction(
        recipientAddress: String,
        amountSol: Double
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
                // Re-authorize with stored token if available
                val token = _authToken.value
                if (token != null) {
                    client.reauthorize(
                        Uri.parse("https://aura.app"),
                        Uri.parse("favicon.ico"),
                        "Aura",
                        token
                    ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                } else {
                    client.authorize(
                        Uri.parse("https://aura.app"),
                        Uri.parse("favicon.ico"),
                        "Aura",
                        "mainnet-beta"
                    ).get(CLIENT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                }

                // TODO: Build a real SystemProgram.transfer TX here
                // For now, return a confirmation that the session was established
                "mwa-tx-authorized"
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
    }
}
