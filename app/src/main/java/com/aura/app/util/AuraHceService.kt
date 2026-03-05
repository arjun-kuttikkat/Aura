package com.aura.app.util

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.aura.app.data.AuraRepository

class AuraHceService : HostApduService() {

    private val TAG = "AuraHceService"

    // APDU commands
    private val SELECT_APP = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x07, 0xD2.toByte(), 0x76, 0x00, 0x00, 0x85.toByte(), 0x01, 0x01, 0x00)
    private val SELECT_CC = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x03)
    private val SELECT_NDEF = byteArrayOf(0x00, 0xA4.toByte(), 0x00, 0x0C, 0x02, 0xE1.toByte(), 0x04)
    private val READ_BINARY = byteArrayOf(0x00, 0xB0.toByte())

    private val SUCCESS = byteArrayOf(0x90.toByte(), 0x00)
    private val ERROR = byteArrayOf(0x6A.toByte(), 0x82.toByte()) // File not found

    // CC File Content (15 bytes)
    private val CC_FILE = byteArrayOf(
        0x00, 0x0F, // Length of CC file
        0x20, // Mapping version 2.0
        0x00, 0xFF.toByte(), // Maximum data size for READ
        0x00, 0xFF.toByte(), // Maximum data size for UPDATE
        0x04, 0x06, // T, L of NDEF File Control TLV
        0xE1.toByte(), 0x04, // File identifier of NDEF file
        0x0F, 0x00, // Maximum NDEF file size (approx 4096 bytes)
        0x00, // Read access (granted)
        0x00  // Write access (granted)
    )

    private var selectedFile = 0
    private val CC_FILE_ID = 1
    private val NDEF_FILE_ID = 2

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "processCommandApdu: ${commandApdu.toHex()}")
        
        // Exact match with trailing Le=00 removed if present, check startsWith
        if (commandApdu.size >= SELECT_APP.size - 1 && matchesPrefix(commandApdu, SELECT_APP)) {
            Log.d(TAG, "SELECT_APP")
            return SUCCESS
        }
        
        if (commandApdu.size >= SELECT_CC.size && matchesPrefix(commandApdu, SELECT_CC)) {
            Log.d(TAG, "SELECT_CC")
            selectedFile = CC_FILE_ID
            return SUCCESS
        }
        
        if (commandApdu.size >= SELECT_NDEF.size && matchesPrefix(commandApdu, SELECT_NDEF)) {
            Log.d(TAG, "SELECT_NDEF")
            selectedFile = NDEF_FILE_ID
            return SUCCESS
        }
        
        if (commandApdu.size >= 2 && commandApdu[0] == READ_BINARY[0] && commandApdu[1] == READ_BINARY[1]) {
            val offset = (commandApdu[2].toInt() and 0xFF) shl 8 or (commandApdu[3].toInt() and 0xFF)
            val length = if (commandApdu.size >= 5) commandApdu[4].toInt() and 0xFF else 0
            
            Log.d(TAG, "READ_BINARY offset=$offset length=$length file=$selectedFile")
            
            if (selectedFile == CC_FILE_ID) {
                return readData(CC_FILE, offset, length)
            } else if (selectedFile == NDEF_FILE_ID) {
                val ndef = constructNdefFile()
                return readData(ndef, offset, length)
            }
        }
        
        Log.w(TAG, "Unknown APDU command")
        return ERROR
    }

    private fun matchesPrefix(array: ByteArray, prefix: ByteArray): Boolean {
        // Many Android HCE APIs append Le or change lengths, so we match the core bytes
        val len = Math.min(array.size, prefix.size)
        // For Select APP, the Le byte (00) at the end of our definition might not be sent by the reader.
        val compareLen = if (prefix === SELECT_APP) prefix.size - 1 else prefix.size
        if (array.size < compareLen) return false
        for (i in 0 until compareLen) {
            if (array[i] != prefix[i]) return false
        }
        return true
    }

    private fun readData(data: ByteArray, offset: Int, length: Int): ByteArray {
        if (offset > data.size) return ERROR
        
        // Le could be 0, which means 256 bytes in some contexts, but usually the NDEF reader bounds it.
        val actualLength = if (length == 0) data.size - offset else length
        
        val end = Math.min(offset + actualLength, data.size)
        val responseData = data.copyOfRange(offset, end)
        
        val result = ByteArray(responseData.size + 2)
        System.arraycopy(responseData, 0, result, 0, responseData.size)
        result[result.size - 2] = SUCCESS[0]
        result[result.size - 1] = SUCCESS[1]
        
        return result
    }

    private fun constructNdefFile(): ByteArray {
        val tradeId = AuraRepository.currentTradeSession.value?.id
        if (tradeId == null) {
            // No active trade session — refuse to emit HCE data
            Log.w(TAG, "No active trade session — HCE service cannot construct NDEF")
            return byteArrayOf()  // Return empty file; reader will see no NDEF record
        }
        val quickReceiveUrl = AuraRepository.activeQuickReceiveUri.value
        val url = quickReceiveUrl ?: "https://aura.so/pay/$tradeId"
        Log.d(TAG, "Constructing NDEF for URL: $url")
        
        val record = NdefRecord.createUri(url)
        val ndefMessage = NdefMessage(record)
        val ndefBytes = ndefMessage.toByteArray()
        
        val fileLength = ndefBytes.size
        // First 2 bytes are the length of the NDEF message
        val fileData = ByteArray(fileLength + 2)
        fileData[0] = ((fileLength shr 8) and 0xFF).toByte()
        fileData[1] = (fileLength and 0xFF).toByte()
        System.arraycopy(ndefBytes, 0, fileData, 2, fileLength)
        
        return fileData
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "onDeactivated: reason=$reason")
        selectedFile = 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}
