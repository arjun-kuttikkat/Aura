package com.aura.app.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
import com.aura.app.model.EscrowState
import com.aura.app.model.TradeState
import com.aura.app.ui.components.GlassCard
import com.aura.app.ui.theme.DarkBase
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscrowPayScreen(
    onComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val session by AuraRepository.currentTradeSession.collectAsState(initial = null)
    val listing = session?.let { AuraRepository.getListing(it.listingId) }
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<EscrowState?>(null) }
    var txSig by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val isLocked = status == EscrowState.LOCKED || txSig != null
    val lockScale by animateFloatAsState(
        targetValue = if (isLocked) 1.1f else 1f,
        label = "lockScale",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay / Escrow") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBase,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = DarkBase,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            listing?.let {
                GlassCard(modifier = Modifier.fillMaxWidth(), glowColor = Orange500) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            it.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "%.2f SOL".format(it.priceLamports / 1_000_000_000.0),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Orange500,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).scale(lockScale),
                    tint = if (isLocked) Gold500 else Orange500,
                )
            }

            if (walletAddress == null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Wallet Not Connected", style = MaterialTheme.typography.titleMedium)
                        Text("Connect your Solana wallet to proceed.")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                isLoading = true
                                WalletConnectionState.connect(
                                    scope = scope,
                                    onSuccess = { isLoading = false },
                                    onError = { isLoading = false; errorMsg = it.message }
                                )
                            },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black),
                        ) {
                            Text("Connect Wallet")
                        }
                    }
                }
            } else if (status == null || status == EscrowState.PENDING) {
                Text(
                    "Paying with: ${walletAddress?.take(4)}...${walletAddress?.takeLast(4)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        val amount = listing?.priceLamports ?: 0L
                        val amountSol = amount / 1_000_000_000.0
                        isLoading = true
                        errorMsg = null
                        scope.launch {
                            WalletConnectionState.signAndSendTransaction(
                                scope = scope,
                                recipientAddress = session?.sellerWallet ?: listing?.sellerWallet ?: return@launch,
                                amountSol = amountSol,
                                onSuccess = { sig ->
                                    isLoading = false
                                    txSig = sig
                                    status = EscrowState.LOCKED
                                    AuraRepository.updateTradeState(TradeState.ESCROW_LOCKED)
                                },
                                onError = { e ->
                                    isLoading = false
                                    errorMsg = e.message ?: "Transaction failed"
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Orange500,
                        contentColor = Color.Black,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.Black,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Signing with wallet...")
                    } else {
                        Text("Pay %.2f SOL".format(listing?.priceLamports?.div(1_000_000_000.0) ?: 0.0), fontWeight = FontWeight.Bold)
                    }
                }
            }

            errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (isLocked) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    glowColor = Gold500,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Gold500, modifier = Modifier.size(32.dp))
                        Text("Escrow Secured ✓", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Gold500)
                        Text("Funds locked in vault.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        AuraRepository.updateTradeState(TradeState.COMPLETE)
                        onComplete()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold500, contentColor = Color.Black),
                ) {
                    Text("Complete Trade & Release Goods", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
