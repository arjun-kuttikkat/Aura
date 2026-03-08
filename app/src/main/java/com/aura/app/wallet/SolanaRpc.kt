package com.aura.app.wallet

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SolanaRpc {

    /**
     * RPC URL. Uses HELIUS_RPC_URL from local.properties if set; else builds from HELIUS_API_KEY.
     * Set SOLANA_NETWORK=devnet / devnet RPC when the escrow program is on devnet.
     */
    var rpcUrl: String = com.aura.app.BuildConfig.HELIUS_RPC_URL
        .takeIf { it.isNotBlank() }
        ?: "https://mainnet.helius-rpc.com/?api-key=${com.aura.app.BuildConfig.HELIUS_KEY}"

    /** Fallback public RPC when primary fails. */
    private const val FALLBACK_RPC = "https://api.mainnet-beta.solana.com"

    private const val TAG = "SolanaRpc"
    private const val MAX_RETRIES = 3
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * Fetches the latest confirmed blockhash. Tries primary RPC first (with retries),
     * then fallback public RPC so the user can still complete payment if Helius is down or key is missing.
     */
    suspend fun getLatestBlockhash(): String? {
        val primary = getLatestBlockhashFromUrl(rpcUrl)
        if (primary != null) return primary
        Log.w(TAG, "Primary RPC blockhash failed, trying fallback $FALLBACK_RPC")
        return getLatestBlockhashFromUrl(FALLBACK_RPC)
    }

    private suspend fun getLatestBlockhashFromUrl(url: String): String? = withRetry {
        val body = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"confirmed"}]}"""
        val response = rpcPost(body, url) ?: return@withRetry null

        // Handle JSON-RPC error object (e.g. {"jsonrpc":"2.0","id":1,"error":{...}})
        if (response.contains("\"error\"")) {
            Log.e(TAG, "RPC error response: $response")
            return@withRetry null
        }

        Regex(""""blockhash"\s*:\s*"([1-9A-HJ-NP-Za-km-z]{32,44})"""")
            .find(response)
            ?.groupValues
            ?.get(1)
            .also { hash ->
                if (hash == null) Log.e(TAG, "Blockhash not found in response: $response")
                else Log.d(TAG, "Blockhash: $hash")
            }
    }

    /**
     * Fetches the SOL balance for a pubkey in lamports.
     * Returns null on failure.
     */
    suspend fun getBalance(pubkey: String): Long? = withRetry {
        val body = """{"jsonrpc":"2.0","id":1,"method":"getBalance","params":["$pubkey",{"commitment":"confirmed"}]}"""
        val response = rpcPost(body, rpcUrl) ?: return@withRetry null

        Regex(""""value"\s*:\s*(\d+)""")
            .find(response)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            .also { balance ->
                if (balance == null) Log.e(TAG, "Balance not found: $response")
                else Log.d(TAG, "Balance for $pubkey: $balance lamports (${balance / 1_000_000_000.0} SOL)")
            }
    }

    /**
     * Fetches minimum balance for rent exemption for a given data size.
     */
    suspend fun getMinimumBalanceForRentExemption(dataSize: Int): Long? = withRetry {
        val body = """{"jsonrpc":"2.0","id":1,"method":"getMinimumBalanceForRentExemption","params":[$dataSize]}"""
        val response = rpcPost(body, rpcUrl) ?: return@withRetry null

        Regex(""""result"\s*:\s*(\d+)""")
            .find(response)
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
    }

    /**
     * Returns true when signature has reached at least confirmed/finalized commitment.
     */
    suspend fun isSignatureConfirmed(signature: String): Boolean = withRetry {
        val body = """{"jsonrpc":"2.0","id":1,"method":"getSignatureStatuses","params":[["$signature"],{"searchTransactionHistory":true}]}"""
        val response = rpcPost(body, rpcUrl) ?: return@withRetry null
        val status = Regex(""""confirmationStatus"\s*:\s*"(processed|confirmed|finalized)"""")
            .find(response)
            ?.groupValues
            ?.get(1)
            ?: return@withRetry false
        status == "confirmed" || status == "finalized"
    } ?: false

    /**
     * Pre-flight simulate the transaction before sending to the wallet.
     * Uses replaceRecentBlockhash so the RPC substitutes a fresh blockhash for simulation.
     * Returns null on success; on failure returns a SimulateResult with the RPC error.
     * Call before signAndSendTransaction to surface simulation failures early.
     */
    suspend fun simulateTransaction(txBytes: ByteArray): SimulateResult? {
        val base64 = android.util.Base64.encodeToString(txBytes, android.util.Base64.NO_WRAP)
        val body = """{"jsonrpc":"2.0","id":1,"method":"simulateTransaction","params":["$base64",{"encoding":"base64","commitment":"confirmed","replaceRecentBlockhash":true}]}"""
        val response = rpcPost(body, rpcUrl) ?: return SimulateResult.Failed("RPC connection failed")
        if (response.contains("\"error\"")) {
            val errMatch = """"message"\s*:\s*"([^"]*)"""".toRegex().find(response)
            return SimulateResult.Failed(errMatch?.groupValues?.get(1) ?: response.take(200))
        }
        // result.value.err is null on success
        if (""""err"\s*:\s*null""".toRegex().containsMatchIn(response)) return null
        val errRegex = """"err"\s*:\s*"([^"]+)"|"InstructionError"\s*:\s*\[([^\]]+)\]""".toRegex()
        val errMatch = errRegex.find(response)
        val logsRegex = """"logs"\s*:\s*\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val logsMatch = logsRegex.find(response)
        val logs = logsMatch?.groupValues?.get(1)?.take(500) ?: ""
        val errMsg = errMatch?.groupValues?.get(1) ?: errMatch?.groupValues?.get(2) ?: "Simulation failed"
        Log.e(TAG, "simulateTransaction failed: $errMsg logs=$logs")
        return SimulateResult.Failed("$errMsg. ${if (logs.isNotBlank()) "Logs: $logs" else ""}")
    }

    sealed class SimulateResult {
        data class Failed(val message: String) : SimulateResult()
    }

    /**
     * Poll transaction status until confirmed/finalized or timeout.
     * Uses extended default timeout (120s) to avoid "Phantom Signatures" where payment
     * succeeds on-chain but UI showed failed due to minor RPC congestion.
     */
    suspend fun waitForSignatureConfirmation(
        signature: String,
        timeoutMs: Long = 120_000L,
        pollMs: Long = 1_500L,
    ): Boolean {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (isSignatureConfirmed(signature)) return true
            kotlinx.coroutines.delay(pollMs)
        }
        return false
    }

    // ── Internal RPC Call ─────────────────────────────────────────

    private suspend fun rpcPost(body: String, url: String = rpcUrl): String? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                val errBody = try {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.readText()?.take(200) ?: ""
                } catch (_: Exception) { "" }
                Log.e(TAG, "HTTP ${connection.responseCode} from RPC: $errBody")
                return@withContext null
            }

            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            Log.e(TAG, "RPC call failed", e)
            null
        }
    }

    // ── Retry with Exponential Backoff ────────────────────────────

    private suspend fun <T> withRetry(block: suspend () -> T?): T? {
        var lastError: Exception? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val result = block()
                if (result != null) return result
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Attempt ${attempt + 1}/$MAX_RETRIES failed", e)
            }
            if (attempt < MAX_RETRIES - 1) {
                kotlinx.coroutines.delay((1000L * (attempt + 1))) // 1s, 2s backoff
            }
        }
        Log.e(TAG, "All $MAX_RETRIES attempts failed", lastError)
        return null
    }
}
