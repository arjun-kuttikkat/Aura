package com.aura.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.ui.graphics.toArgb
import com.aura.app.navigation.NavGraph
import com.aura.app.ui.theme.AuraTheme
import com.aura.app.ui.theme.DarkBase
import com.aura.app.util.NfcHandoverManager
import com.aura.app.wallet.WalletConnectionState
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.aura.app.data.OfficialListingSeeder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set system bar colors to match app background before any content
        val barColor = DarkBase.toArgb()
        window.statusBarColor = barColor
        window.navigationBarColor = barColor
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                lightScrim = barColor,
                darkScrim = barColor,
            ) { true },
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = barColor,
                darkScrim = barColor,
            ) { true },
        )
        try {
            com.aura.app.data.AuraPreferences.init(applicationContext)
            com.aura.app.data.SupabaseClient.appContext = applicationContext
            com.aura.app.data.AuraRepository.appContext = applicationContext
            WalletConnectionState.init { intent ->
                startActivity(intent)
            }
            try {
                NfcHandoverManager.init(this)
            } catch (e: Exception) {
                Log.w("MainActivity", "NFC init failed (NFC may be unavailable)", e)
            }
            try {
                com.aura.app.data.HotzoneManager.init(applicationContext)
            } catch (e: Exception) {
                Log.w("MainActivity", "HotzoneManager init failed", e)
            }
            com.aura.app.data.DirectivesManager.generateDailyDirectives()
            lifecycleScope.launch {
                OfficialListingSeeder.seedIfNeeded(applicationContext)
                com.aura.app.data.AuraRepository.refreshListingsAwait()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Init failed", e)
        }
        setContent {
            AuraTheme {
                NavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            NfcHandoverManager.enable(this)
        } catch (e: Exception) {
            Log.w("MainActivity", "NFC enable failed", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            NfcHandoverManager.disable(this)
        } catch (e: Exception) {
            Log.w("MainActivity", "NFC disable failed", e)
        }
    }
}
