package com.aura.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aura.app.wallet.WalletService
import kotlinx.coroutines.launch

@Composable
fun WalletScreen(modifier: Modifier = Modifier) {
    val walletService = remember { WalletService() }
    val scope = rememberCoroutineScope()
    var resultText by mutableStateOf<String?>(null)
    var isLoading by mutableStateOf(false)
    val scrollState = rememberScrollState()

    val placeholderTxBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Wallet",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                isLoading = true
                resultText = null
                scope.launch {
                    walletService.connect()
                        .onSuccess { resultText = "Connected: $it" }
                        .onFailure { resultText = "Error: ${it.message}" }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Connect Wallet")
        }

        Button(
            onClick = {
                isLoading = true
                resultText = null
                scope.launch {
                    walletService.signMessage("Hello Aura".toByteArray())
                        .onSuccess { resultText = "Signed ${it.size} bytes" }
                        .onFailure { resultText = "Error: ${it.message}" }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Sign Message")
        }

        Button(
            onClick = {
                isLoading = true
                resultText = null
                scope.launch {
                    walletService.signAndSendTransaction(placeholderTxBytes)
                        .onSuccess { resultText = "Signature: $it" }
                        .onFailure { resultText = "Error: ${it.message}" }
                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text("Sign & Send Tx")
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        resultText?.let { text ->
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
