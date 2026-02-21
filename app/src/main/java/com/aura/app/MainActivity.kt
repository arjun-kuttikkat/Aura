package com.aura.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aura.app.navigation.NavGraph
import com.aura.app.ui.theme.AuraTheme
import com.aura.app.util.NfcHandoverManager
import com.aura.app.wallet.WalletConnectionState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WalletConnectionState.init { intent ->
            startActivity(intent)
        }
        NfcHandoverManager.init(this)
        enableEdgeToEdge()
        setContent {
            AuraTheme {
                NavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable NFC reader mode whenever the app is in the foreground.
        // NfcHandoverManager.enable() is a no-op when scanner is not needed —
        // each screen controls the active scanning via state observation.
        NfcHandoverManager.enable(this)
    }

    override fun onPause() {
        super.onPause()
        NfcHandoverManager.disable(this)
    }
}