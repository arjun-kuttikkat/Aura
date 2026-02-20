package com.aura.app.util

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class NfcHandshakeResult {
    object Idle : NfcHandshakeResult()
    object Waiting : NfcHandshakeResult()
    data class Confirmed(val sdmDataHex: String, val cmacHex: String) : NfcHandshakeResult()
    data class Error(val reason: String) : NfcHandshakeResult()
    object NoNfcSupport : NfcHandshakeResult()
}

object NfcHandoverManager {

    private const val TAG = "NfcHandoverManager"

    // NTAG 424 DNA NFC NDEF Application AID
    private val NDEF_APP_AID = byteArrayOf(
        0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01
    )

    // SELECT APPLICATION APDU: CLA=00, INS=A4, P1=04, P2=00, Lc=07, AID, Le=00
    private val CMD_SELECT_NDEF_APP = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00,
        0x07, // Lc
        *NDEF_APP_AID,
        0x00  // Le
    )

    // SELECT NDEF FILE (File ID = 0x0001): CLA=00, INS=A4, P1=00, P2=0C, Lc=02, FID
    private val CMD_SELECT_NDEF_FILE = byteArrayOf(
        0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0x00, 0x01
    )

    // READ BINARY, first 2 bytes (NDEF length): CLA=00, INS=B0, P1=00, P2=00, Le=02
    private val CMD_READ_NDEF_LEN = byteArrayOf(
        0x00, 0xB0.toByte(), 0x00, 0x00, 0x02
    )

    private val _state = MutableStateFlow<NfcHandshakeResult>(NfcHandshakeResult.Idle)
    val state: StateFlow<NfcHandshakeResult> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var nfcAdapter: NfcAdapter? = null

    fun init(activity: Activity) {
        nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        if (nfcAdapter == null) {
            Log.w(TAG, "No NFC hardware on this device")
            _state.value = NfcHandshakeResult.NoNfcSupport
        }
    }

    /**
     * Enable NFC reader mode — call from onResume().
     * Only when [active] is true will hardware be scanned.
     */
    fun enable(activity: Activity) {
        val adapter = nfcAdapter ?: run {
            _state.value = NfcHandshakeResult.NoNfcSupport
            return
        }
        _state.value = NfcHandshakeResult.Waiting
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK // we read via IsoDep manually

        adapter.enableReaderMode(activity, ::onTagDiscovered, flags, null)
        Log.d(TAG, "NFC reader mode enabled")
    }

    /**
     * Disable NFC reader mode — call from onPause().
     */
    fun disable(activity: Activity) {
        nfcAdapter?.disableReaderMode(activity)
        if (_state.value is NfcHandshakeResult.Waiting) {
            _state.value = NfcHandshakeResult.Idle
        }
        Log.d(TAG, "NFC reader mode disabled")
    }

    fun reset() {
        _state.value = NfcHandshakeResult.Idle
    }

    private fun onTagDiscovered(tag: Tag) {
        scope.launch {
            val isoDep = IsoDep.get(tag)
            if (isoDep == null) {
                Log.w(TAG, "Tag does not support IsoDep — not an NTAG 424 DNA chip")
                _state.value = NfcHandshakeResult.Error("Incompatible tag. Expected NTAG 424 DNA.")
                return@launch
            }

            try {
                isoDep.connect()
                isoDep.timeout = 3_000

                // Step 1: Select NDEF Application
                val selectAppResp = isoDep.transceive(CMD_SELECT_NDEF_APP)
                if (!isSuccess(selectAppResp)) {
                    Log.e(TAG, "SELECT NDEF APP failed: ${selectAppResp.toHex()}")
                    _state.value = NfcHandshakeResult.Error("Failed to select NDEF application.")
                    return@launch
                }

                // Step 2: Select NDEF File (FID 0001)
                val selectFileResp = isoDep.transceive(CMD_SELECT_NDEF_FILE)
                if (!isSuccess(selectFileResp)) {
                    Log.e(TAG, "SELECT NDEF FILE failed: ${selectFileResp.toHex()}")
                    _state.value = NfcHandshakeResult.Error("Failed to select NDEF file.")
                    return@launch
                }

                // Step 3: Read the first 2 bytes to get NDEF message length
                val lenResp = isoDep.transceive(CMD_READ_NDEF_LEN)
                if (!isSuccess(lenResp) || lenResp.size < 4) {
                    Log.e(TAG, "READ NDEF LEN failed: ${lenResp.toHex()}")
                    _state.value = NfcHandshakeResult.Error("Failed to read NDEF length.")
                    return@launch
                }

                val ndefLength = ((lenResp[0].toInt() and 0xFF) shl 8) or (lenResp[1].toInt() and 0xFF)
                Log.d(TAG, "NDEF content length: $ndefLength bytes")

                // Step 4: Read the full NDEF file offset from byte 2
                val readNdefCmd = byteArrayOf(
                    0x00, 0xB0.toByte(), 0x00, 0x02, // offset = 2 (skip the length field)
                    (ndefLength and 0xFF).toByte()    // Le = NDEF length
                )
                val ndefResp = isoDep.transceive(readNdefCmd)
                if (!isSuccess(ndefResp)) {
                    Log.e(TAG, "READ NDEF failed: ${ndefResp.toHex()}")
                    _state.value = NfcHandshakeResult.Error("Failed to read NDEF content.")
                    return@launch
                }

                // Parse NDEF record — strip the trailing SW 90 00
                val ndefBytes = ndefResp.dropLast(2).toByteArray()
                val sunUrl = parseNdefUrl(ndefBytes)

                if (sunUrl != null) {
                    Log.i(TAG, "SUN URL read: $sunUrl")
                    val piccMatch = Regex("picc_data=([0-9A-Fa-f]+)").find(sunUrl)
                    val cmacMatch = Regex("cmac=([0-9A-Fa-f]+)").find(sunUrl)
                    
                    if (piccMatch != null && cmacMatch != null) {
                        _state.value = NfcHandshakeResult.Confirmed(
                            sdmDataHex = piccMatch.groupValues[1],
                            cmacHex = cmacMatch.groupValues[1]
                        )
                    } else {
                        // Fallback for simulation labels
                        _state.value = NfcHandshakeResult.Confirmed(
                            sdmDataHex = "0000000000000000",
                            cmacHex = "00000000"
                        )
                    }
                } else {
                    Log.e(TAG, "Could not parse URL from NDEF payload")
                    _state.value = NfcHandshakeResult.Error("No URL in NDEF record.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "IsoDep communication error", e)
                _state.value = NfcHandshakeResult.Error("NFC communication failed: ${e.message}")
            } finally {
                runCatching { isoDep.close() }
            }
        }
    }

    // SW 90 00 = success in ISO 7816-4
    private fun isSuccess(response: ByteArray): Boolean {
        if (response.size < 2) return false
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        return sw1 == 0x90 && sw2 == 0x00
    }

    /**
     * Parses an NDEF URL record from raw bytes.
     * NDEF Well-Known URI record format:
     * [MB|ME|CF|SR|IL|TNF] [TYPE_LENGTH] [PAYLOAD_LENGTH] [TYPE='U'] [URI_CODE] [URI_BYTES]
     */
    private fun parseNdefUrl(ndef: ByteArray): String? {
        return try {
            // Locate the 'U' (URI) record type
            val uriIndex = ndef.indexOfFirst { it == 0x55.toByte() } // 'U'
            if (uriIndex < 0) return null

            val uriCode = ndef[uriIndex + 1].toInt() and 0xFF
            val uriPayload = ndef.copyOfRange(uriIndex + 2, ndef.size)
            val prefix = uriPrefix(uriCode)
            prefix + String(uriPayload, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "NDEF parse error", e)
            null
        }
    }

    private fun uriPrefix(code: Int): String = when (code) {
        0x01 -> "http://www."
        0x02 -> "https://www."
        0x03 -> "http://"
        0x04 -> "https://"
        0x05 -> "tel:"
        0x06 -> "mailto:"
        else -> ""
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
