package com.aura.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.app.data.MockBackend
import com.aura.app.model.EscrowState
import com.aura.app.model.TradeState
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscrowPayScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val session by MockBackend.currentTradeSession.collectAsState(initial = null)
    val listing = session?.let { MockBackend.getListing(it.listingId) }
    
    // Wallet State
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    
    var status by remember { mutableStateOf<EscrowState?>(null) }
    var txSig by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay / Escrow") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listing?.let {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = "%.2f SOL".format(it.priceLamports / 1_000_000_000.0),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Connection Prompt
            if (walletAddress == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Wallet Not Connected", style = MaterialTheme.typography.titleMedium)
                        Text("Connect your Solana wallet to proceed with payment.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                WalletConnectionState.connect(
                                    scope = scope,
                                    onSuccess = { isLoading = false },
                                    onError = { 
                                        isLoading = false 
                                        errorMsg = it.message
                                    }
                                )
                            },
                            enabled = !isLoading
                        ) {
                            Text("Connect Wallet")
                        }
                    }
                }
            } else {
                Text("Paying with: ${walletAddress?.take(4)}...${walletAddress?.takeLast(4)}")
                
                if (status == null || status == EscrowState.PENDING) {
                    Button(
                        onClick = {
                            val amount = listing?.priceLamports ?: 0L
                            val amountSol = amount / 1_000_000_000.0
                            
                            isLoading = true
                            errorMsg = null
                            
                            scope.launch {
                                WalletConnectionState.signAndSendTransaction(
                                    scope = scope,
                                    recipientAddress = "EscrowVault_SIMULATED", // Hardcoded for demo plan
                                    amountSol = amountSol,
                                    onSuccess = { sig ->
                                        isLoading = false
                                        txSig = sig
                                        status = EscrowState.LOCKED
                                        MockBackend.updateTradeState(TradeState.ESCROW_LOCKED)
                                    },
                                    onError = { e ->
                                        isLoading = false
                                        errorMsg = e.message ?: "Transaction failed"
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading,
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("Confirmed: Pay %.2f SOL".format(listing?.priceLamports?.div(1_000_000_000.0) ?: 0.0))
                        }
                    }
                }
            }
            
            errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            
            if (status == EscrowState.LOCKED || txSig != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Escrow Locked", style = MaterialTheme.typography.titleMedium)
                        Text("Funds secured in Vault.", style = MaterialTheme.typography.bodyMedium)
                        txSig?.let { Text("Sig: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = {
                        MockBackend.updateTradeState(TradeState.COMPLETE)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Text("Complete Trade & Release Goods")
                }
            }
        }
    }
}
