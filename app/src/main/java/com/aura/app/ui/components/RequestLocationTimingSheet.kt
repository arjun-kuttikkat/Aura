package com.aura.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aura.app.data.MeetupPreferences
import com.aura.app.data.MeetupPreferencesStore
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.Orange500
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FetchPlaceResponse
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsResponse
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** 30-min intervals for the day: 00:00, 00:30, 01:00, ... 23:30 */
private val TIME_SLOTS = (0..47).map { i ->
    val h = i / 2
    val m = (i % 2) * 30
    "%02d:%02d".format(h, m)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestLocationTimingSheet(
    listingId: String,
    onDismiss: () -> Unit,
    onSaved: (address: String, time: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var predictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var selectedAddress by remember { mutableStateOf<String?>(null) }
    var selectedLat by remember { mutableStateOf<Double?>(null) }
    var selectedLng by remember { mutableStateOf<Double?>(null) }
    var selectedTime by remember { mutableStateOf(TIME_SLOTS[28]) } // default 14:00
    var isExpanded by remember { mutableStateOf(false) }
    var isLoadingPlace by remember { mutableStateOf(false) }

    val placesClient = remember { Places.createClient(context) }
    var sessionToken by remember { mutableStateOf(AutocompleteSessionToken.newInstance()) }

    // Debounced autocomplete search (skip when location already selected)
    LaunchedEffect(searchQuery, selectedAddress) {
        if (selectedAddress != null) {
            predictions = emptyList()
            return@LaunchedEffect
        }
        if (searchQuery.isBlank()) {
            predictions = emptyList()
            return@LaunchedEffect
        }
        val query = searchQuery.trim()
        if (query.length < 2) {
            predictions = emptyList()
            return@LaunchedEffect
        }
        isSearching = true
        delay(350)
        try {
            val request = FindAutocompletePredictionsRequest.builder()
                .setQuery(query)
                .setSessionToken(sessionToken)
                .build()
            val response = withContext(Dispatchers.IO) {
                placesClient.findAutocompletePredictions(request).await()
            }
            predictions = response.getAutocompletePredictions() ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("RequestLocationTiming", "Places autocomplete failed: ${e.message}", e)
            predictions = emptyList()
        } finally {
            isSearching = false
        }
    }

    fun onPredictionSelected(prediction: AutocompletePrediction) {
        searchQuery = prediction.getPrimaryText(null).toString() + ", " + prediction.getSecondaryText(null).toString()
        predictions = emptyList()
        isLoadingPlace = true
        scope.launch {
            try {
                val fields = listOf<Place.Field>(
                    Place.Field.LOCATION,
                    Place.Field.FORMATTED_ADDRESS,
                    Place.Field.DISPLAY_NAME,
                )
                val token = sessionToken
                val request = FetchPlaceRequest.builder(prediction.getPlaceId(), fields)
                    .setSessionToken(token)
                    .build()
                val response: FetchPlaceResponse = withContext(Dispatchers.IO) {
                    placesClient.fetchPlace(request).await()
                }
                val place = response.place
                val location = place.getLocation()
                if (location != null) {
                    selectedLat = location.latitude
                    selectedLng = location.longitude
                    selectedAddress = place.getFormattedAddress()
                        ?: place.getDisplayName()
                        ?: prediction.getPrimaryText(null).toString()
                } else {
                    selectedAddress = prediction.getPrimaryText(null).toString() + ", " + prediction.getSecondaryText(null).toString()
                }
            } catch (e: Exception) {
                selectedAddress = prediction.getPrimaryText(null).toString() + ", " + prediction.getSecondaryText(null).toString()
            } finally {
                isLoadingPlace = false
                sessionToken = AutocompleteSessionToken.newInstance()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DarkCard,
        dragHandle = { Text("Request Location & Time", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Location step: inline search (avoids PlaceAutocompleteActivity font crash)
            Text("1. Search for a meetup location (anywhere in the world)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { if (selectedAddress == null) searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                readOnly = selectedAddress != null,
                placeholder = { Text("Type address or place name...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Orange500, modifier = Modifier.size(22.dp))
                },
                trailingIcon = {
                    if (isSearching) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Orange500)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Orange500,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedLeadingIconColor = Orange500,
                ),
            )
            // No results hint (API may be disabled)
            if (searchQuery.length >= 2 && !isSearching && predictions.isEmpty() && selectedAddress == null) {
                Text(
                    "No results. Enable \"Places API (New)\" in Google Cloud Console for your API key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            // Predictions dropdown
            if (predictions.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    predictions.forEach { pred ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPredictionSelected(pred) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Orange500, modifier = Modifier.size(20.dp))
                            Column {
                                Text(pred.getPrimaryText(null).toString(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(pred.getSecondaryText(null).toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // Selected location display
            if (selectedAddress != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Orange500.copy(alpha = 0.15f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Orange500, modifier = Modifier.size(24.dp))
                    Column {
                        Text("Selected:", style = MaterialTheme.typography.labelSmall, color = Orange500)
                        Text(
                            if (isLoadingPlace) "Loading..." else selectedAddress!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            // Time step
            Text("2. Preferred time (30-min intervals)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedTime,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Orange500, modifier = Modifier.size(22.dp))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Orange500,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedLeadingIconColor = Orange500,
                    ),
                )
                DropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false },
                    modifier = Modifier.exposedDropdownSize(),
                ) {
                    TIME_SLOTS.forEach { slot ->
                        DropdownMenuItem(
                            text = { Text(slot) },
                            onClick = {
                                selectedTime = slot
                                isExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val lat = selectedLat
                    val lng = selectedLng
                    val addr = selectedAddress
                    if (lat != null && lng != null && !addr.isNullOrBlank()) {
                        MeetupPreferencesStore.save(
                            context,
                            listingId,
                            MeetupPreferences(
                                listingId = listingId,
                                latitude = lat,
                                longitude = lng,
                                address = addr,
                                preferredTime = selectedTime,
                            ),
                        )
                        onSaved(addr, selectedTime)
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black),
                enabled = selectedLat != null && selectedLng != null && selectedAddress != null && !isLoadingPlace,
            ) {
                Text("Save Location & Time", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
