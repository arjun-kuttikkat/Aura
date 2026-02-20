package com.aura.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aura.app.navigation.NavGraph
import com.aura.app.ui.theme.AuraTheme
import com.aura.app.wallet.WalletConnectionState
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WalletConnectionState.init(ActivityResultSender(this))
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                NavGraph()
            }
        }
    }
}