package com.aura.app.ui.screen

import android.app.Activity
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aura.app.data.AuraRepository
import com.aura.app.util.NfcHandoverManager
import com.aura.app.util.NfcHandshakeResult
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import com.aura.app.util.FaceAnalyzer
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class ExchangeMode { SEND, RECEIVE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun P2PExchangeScreen(onBack: () -> Unit) {
    val context = LocalContext.current as Activity
    var mode by remember { mutableStateOf(ExchangeMode.SEND) }
    
    // Receive state
    var receiveAmount by remember { mutableStateOf("") }
    var isLivenessVerifying by remember { mutableStateOf(false) }
    var isLivenessVerified by remember { mutableStateOf(false) }
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    
    // Send state
    val nfcState by NfcHandoverManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var isProcessingTx by remember { mutableStateOf(false) }
    var txSignature by remember { mutableStateOf<String?>(null) }
    var txError by remember { mutableStateOf<String?>(null) }
    
    // Handle NFC Reader state based on mode
    DisposableEffect(mode, isLivenessVerified) {
        if (mode == ExchangeMode.RECEIVE && isLivenessVerified) {
            // Disable reader so HCE can broadcast
            NfcHandoverManager.disable(context)
            val amount = receiveAmount.toDoubleOrNull() ?: 0.0
            val uri = "solana:$walletAddress?amount=$amount"
            AuraRepository.activeQuickReceiveUri.value = uri
        } else if (mode == ExchangeMode.SEND) {
            NfcHandoverManager.enable(context)
            AuraRepository.activeQuickReceiveUri.value = null
        } else {
            // RECEIVE mode but not verified yet - we don't need HCE to broadcast yet
            NfcHandoverManager.enable(context) // keeps default behavior
            AuraRepository.activeQuickReceiveUri.value = null
        }
        
        onDispose {
            AuraRepository.activeQuickReceiveUri.value = null
            NfcHandoverManager.enable(context) // restore default reader mode
        }
    }
    
    // Send mode transaction trigger
    LaunchedEffect(nfcState) {
        if (mode == ExchangeMode.SEND && nfcState is NfcHandshakeResult.Confirmed && !isProcessingTx && txSignature == null) {
            val confirmed = nfcState as NfcHandshakeResult.Confirmed
            if (confirmed.payloadUrl?.startsWith("solana:") == true) {
                isProcessingTx = true
                val uri = Uri.parse(confirmed.payloadUrl)
                // solana:ADDRESS?amount=X
                val recipient = uri.schemeSpecificPart.substringBefore("?")
                val amount = uri.getQueryParameter("amount")?.toDoubleOrNull() ?: 0.0
                val lamports = (amount * 1_000_000_000).toLong()
                
                // Build real SOL transfer transaction
                scope.launch {
                    try {
                        val senderWallet = walletAddress ?: throw Exception("Wallet not connected")
                        val txBytes = com.aura.app.wallet.AnchorTransactionBuilder.buildSolTransferTx(
                            fromPubkey = senderWallet,
                            toPubkey = recipient,
                            lamports = lamports,
                        )
                        val base64Tx = android.util.Base64.encodeToString(txBytes, android.util.Base64.NO_WRAP)
                        
                        WalletConnectionState.signAndSendTransaction(
                            scope = scope,
                            base64EncodedTx = base64Tx,
                            onSuccess = { sig ->
                                txSignature = sig
                                isProcessingTx = false
                            },
                            onError = { e ->
                                txError = e.message ?: "Transaction failed"
                                isProcessingTx = false
                            }
                        )
                    } catch (e: Exception) {
                        txError = e.message ?: "Failed to build transaction"
                        isProcessingTx = false
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("P2P Exchange") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = mode == ExchangeMode.SEND,
                    onClick = { 
                        mode = ExchangeMode.SEND 
                        txSignature = null
                        txError = null
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Send (Payer)")
                }
                SegmentedButton(
                    selected = mode == ExchangeMode.RECEIVE,
                    onClick = { mode = ExchangeMode.RECEIVE },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("Receive (Payee)")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "mode_switch"
            ) { activeMode ->
                if (activeMode == ExchangeMode.SEND) {
                    // SENDER UI
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (txSignature != null) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.Green, modifier = Modifier.size(64.dp))
                            Text("Transaction Sent Successfully!", style = MaterialTheme.typography.titleLarge)
                            Text("Signature: ${txSignature?.take(8)}...${txSignature?.takeLast(8)}")
                            Button(onClick = onBack) { Text("Done") }
                        } else if (txError != null) {
                            Text("Error: $txError", color = MaterialTheme.colorScheme.error)
                            Button(onClick = { 
                                txError = null
                                NfcHandoverManager.reset()
                                NfcHandoverManager.enable(context)
                            }) { Text("Retry") }
                        } else if (isProcessingTx) {
                            CircularProgressIndicator()
                            Text("Signing transaction securely via MWA...")
                        } else {
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse_sender")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f, targetValue = 2.5f,
                                animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutLinearInEasing), repeatMode = RepeatMode.Restart), label = "scale"
                            )
                            val pulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.8f, targetValue = 0f,
                                animationSpec = infiniteRepeatable(animation = tween(1500, easing = FastOutLinearInEasing), repeatMode = RepeatMode.Restart), label = "alpha"
                            )
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier.size(96.dp).scale(pulseScale).alpha(pulseAlpha).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                )
                                Icon(Icons.Default.Nfc, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
                            }
                            Text("Ready to Send", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "Tap your phone to the merchant's Aura device to instantly pay over a secure zero-trust NFC connection.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    // RECEIVER UI
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (!isLivenessVerified && !isLivenessVerifying) {
                            OutlinedTextField(
                                value = receiveAmount,
                                onValueChange = { receiveAmount = it },
                                label = { Text("Amount to Request (SOL)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (receiveAmount.isNotEmpty()) {
                                        isLivenessVerifying = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = receiveAmount.isNotEmpty()
                            ) {
                                Text("Generate Secure Request")
                            }
                        } else if (isLivenessVerifying) {
                            val lifecycleOwner = LocalLifecycleOwner.current
                            Box(modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(16.dp))) {
                                AndroidView(
                                    factory = { ctx ->
                                        val previewView = PreviewView(ctx).apply {
                                            scaleType = PreviewView.ScaleType.FILL_CENTER
                                        }
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            val cameraProvider = cameraProviderFuture.get()
                                            val preview = Preview.Builder().build().also {
                                                it.setSurfaceProvider(previewView.surfaceProvider)
                                            }
                                            val imageAnalysis = ImageAnalysis.Builder()
                                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                                .build()
                                                .also {
                                                    it.setAnalyzer(Executors.newSingleThreadExecutor(), FaceAnalyzer(
                                                        onFaceDetected = {
                                                            // Face recognized mapping to physical presence
                                                            isLivenessVerified = true
                                                            isLivenessVerifying = false
                                                        },
                                                        onNoFaceDetected = {}
                                                    ))
                                                }
                                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                                            try {
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    cameraSelector,
                                                    preview,
                                                    imageAnalysis
                                                )
                                            } catch (exc: Exception) {
                                                android.util.Log.e("P2P", "Camera binding failed", exc)
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                        previewView
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    CircularProgressIndicator(color = Color.Green)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Verifying physical presence...", color = Color.White, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        } else {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = com.aura.app.ui.theme.Orange500, modifier = Modifier.size(96.dp))
                            Text("Liveness Verified", color = Color.Green)
                            Text("Broadcasting Request", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "Your phone is now acting as a secure hardware terminal. Tell the buyer to tap their phone against yours.",
                                textAlign = TextAlign.Center
                            )
                            
                            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.Gray.copy(alpha=0.1f)).padding(16.dp)) {
                                Text("Payload: solana:${walletAddress?.take(8)}...?amount=$receiveAmount", style = MaterialTheme.typography.labelMedium)
                            }
                            
                            Button(onClick = { isLivenessVerified = false }, modifier = Modifier.fillMaxWidth()) {
                                Text("Cancel Request")
                            }
                        }
                    }
                }
            }
        }
    }
}
