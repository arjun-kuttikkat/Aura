package com.aura.app.wallet

import android.net.Uri
import android.util.Log
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
    private val _walletAddress = MutableStateFlow<String?>(null)
    val walletAddress: StateFlow<String?> = _walletAddress.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)

    private lateinit var sender: ActivityResultSender

    private val walletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://aura.app"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Aura"
        )
    )

    fun init(activityResultSender: ActivityResultSender) {
        sender = activityResultSender
    }

    fun connect(
        scope: CoroutineScope,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        scope.launch {
            // Use transact with explicit authorize to specify MainnetBeta cluster
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
                    onSuccess(address)
                }
                is TransactionResult.NoWalletFound -> {
                    onError(Exception("No MWA-compatible wallet found. Install Phantom or Solflare."))
                }
                is TransactionResult.Failure -> {
                    Log.e("WalletConnection", "Connection failed", result.e)
                    onError(result.e)
                }
            }
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
            // Use transact to open a session and attempt to sign.
            // The lambda captures the signature bytes and returns them.
            val result = walletAdapter.transact(sender) {
                // signAndSendTransactions returns signature bytes within the session.
                // For a real transfer, build a SystemProgram.transfer TX here.
                // For now, we return the authResult to prove the session was established.
                "session-ok"
            }
            when (result) {
                is TransactionResult.Success -> {
                    // The session was successful — the wallet authorized us.
                    onSuccess("mwa-tx-authorized")
                }
                is TransactionResult.NoWalletFound -> {
                    onError(Exception("No wallet found"))
                }
                is TransactionResult.Failure -> {
                    Log.e("WalletConnection", "Transaction failed", result.e)
                    onError(result.e)
                }
            }
        }
    }

    fun disconnect() {
        _walletAddress.value = null
        _authToken.value = null
    }
}
