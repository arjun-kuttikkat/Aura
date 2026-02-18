package com.aura.app.wallet

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object WalletConnectionState {
    private val _pubkey = MutableStateFlow<String?>(null)
    val pubkey: StateFlow<String?> = _pubkey.asStateFlow()

    fun setConnected(pubkey: String) {
        _pubkey.value = pubkey
    }

    fun disconnect() {
        _pubkey.value = null
    }

    fun isConnected(): Boolean = _pubkey.value != null
}
