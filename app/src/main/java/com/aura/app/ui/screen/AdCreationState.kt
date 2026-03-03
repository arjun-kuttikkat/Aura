package com.aura.app.ui.screen

import android.net.Uri

object AdCreationState {
    var category: String = ""
    var title: String = ""
    var price: Long = 0L
    var condition: String = "Good"
    var selectedImageUri: Uri? = null
    var selectedLocation: String = ""

    fun clear() {
        category = ""
        title = ""
        price = 0L
        condition = "Good"
        selectedImageUri = null
        selectedLocation = ""
    }
}
