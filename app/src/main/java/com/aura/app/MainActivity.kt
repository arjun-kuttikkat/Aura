package com.aura.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aura.app.navigation.NavGraph
import com.aura.app.ui.theme.AuraTheme
import com.aura.app.util.NfcHandoverManager
import com.aura.app.wallet.WalletConnectionState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.aura.app.data.OfficialListingSeeder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.aura.app.data.AuraPreferences.init(applicationContext)
        com.aura.app.data.SupabaseClient.appContext = applicationContext
        WalletConnectionState.init { intent ->
            startActivity(intent)
        }
        NfcHandoverManager.init(this)
        com.aura.app.data.HotzoneManager.init(applicationContext)
        com.aura.app.data.DirectivesManager.generateDailyDirectives()
        
        lifecycleScope.launch {
            // Force re-seed by clearing the flag
            com.aura.app.data.OfficialListingSeeder.clearSeededFlag(applicationContext)
            OfficialListingSeeder.seedIfNeeded(applicationContext)
        }
        
        enableEdgeToEdge()
        setContent {
            val isDark by com.aura.app.data.AuraPreferences.isDarkMode.collectAsState()
            AuraTheme(darkTheme = isDark) {
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
