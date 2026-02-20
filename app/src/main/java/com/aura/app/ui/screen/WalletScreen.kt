package com.aura.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState // Added
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color // Added
import androidx.compose.ui.unit.dp
import com.aura.app.wallet.WalletConnectionState // Updated
import kotlinx.coroutines.launch

@Composable
fun WalletScreen(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    var resultText by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Wallet Connection",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (walletAddress != null) Color(0xFFE0F7FA) else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Status: ${if (walletAddress != null) "Connected" else "Not Connected"}",
                    style = MaterialTheme.typography.titleMedium
                )
                if (walletAddress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Address: $walletAddress",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        if (walletAddress == null) {
            Button(
                onClick = {
                    isLoading = true
                    WalletConnectionState.connect(
                        scope = scope,
                        onSuccess = {
                            isLoading = false
                            resultText = "Connected!"
                        },
                        onError = {
                            isLoading = false
                            resultText = "Error: ${it.message}"
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text("Connect Wallet (Phantom/Solflare)")
                }
            }
        } else {
            Button(
                onClick = {
                    isLoading = true
                    scope.launch {
                        WalletConnectionState.signAndSendTransaction(
                            scope = scope,
                            recipientAddress = "EscrowVaultAddress123",
                            amountSol = 0.1,
                            onSuccess = { sig ->
                                isLoading = false
                                resultText = "Success! Sig: $sig"
                            },
                            onError = { e ->
                                isLoading = false
                                resultText = "Failed: ${e.message}"
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading
            ) {
                 if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                 } else {
                    Text("Simulate Escrow Transaction (0.1 SOL)")
                 }
            }
            
            Button(
                onClick = { WalletConnectionState.disconnect() },
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Disconnect")
            }
        }

        resultText?.let { text ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (text.startsWith("Error")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }
}
