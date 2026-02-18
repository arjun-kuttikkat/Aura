package com.aura.app.ui.screen

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.aura.app.data.MockBackend
import com.aura.app.model.TradeState
import com.aura.app.wallet.WalletConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetSessionScreen(
    onHandshakeComplete: () -> Unit,
    onBack: () -> Unit,
) {
    val session by MockBackend.currentTradeSession.collectAsState(initial = null)
    val pubkey by WalletConnectionState.pubkey.collectAsState()
    var showQr by mutableStateOf(true)

    val qrBitmap = remember(session?.id, pubkey) {
        session?.let { s ->
            val data = "aura:meet:${s.id}:${pubkey ?: ""}"
            generateQrBitmap(data, 256)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meet Session") },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Handshake",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Tap NFC or scan QR to complete handshake",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(modifier = Modifier.height(32.dp))
            if (showQr && qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code",
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color.White),
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text("Show this QR to the other party", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    MockBackend.updateTradeState(TradeState.BOTH_PRESENT)
                    onHandshakeComplete()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Simulate Handshake (dev)")
            }
        }
    }
}

private fun generateQrBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val hints = hashMapOf<EncodeHintType, Int>().apply { put(EncodeHintType.MARGIN, 1) }
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    } catch (e: WriterException) {
        null
    }
}
