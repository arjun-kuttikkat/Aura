package com.aura.app.wallet

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SolanaRpc {

    /**
     * Set this to your dedicated Helius or QuickNode RPC URL before calling any method.
     * Example: "https://mainnet.helius-rpc.com/?api-key=YOUR_KEY"
     */
    var rpcUrl: String = "https://mainnet.helius-rpc.com/?api-key=${com.aura.app.BuildConfig.HELIUS_KEY}"

    private const val TAG = "SolanaRpc"

    /**
     * Fetches the latest confirmed blockhash from the configured RPC endpoint.
     * Returns null on any network or parsing error.
     */
    suspend fun getLatestBlockhash(): String? = withContext(Dispatchers.IO) {
        try {
            val connection = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10_000
                readTimeout = 10_000
            }

            val body = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"confirmed"}]}"""
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP error ${connection.responseCode} from RPC")
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

            // Response shape: { "result": { "value": { "blockhash": "XXXXX", ... } } }
            Regex(""""blockhash"\s*:\s*"([1-9A-HJ-NP-Za-km-z]{32,44})"""")
                .find(response)
                ?.groupValues
                ?.get(1)
                .also { hash ->
                    if (hash == null) Log.e(TAG, "Blockhash not found in response: $response")
                    else Log.d(TAG, "Blockhash: $hash")
                }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch blockhash", e)
            null
        }
    }
}
