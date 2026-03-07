package com.aura.app.ui.screen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aura.app.data.AuraRepository
import com.aura.app.ui.theme.DarkVoid
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SlateElevated
import com.aura.app.util.MeetupLocationUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

private val DEFAULT_DUBAI = LatLng(25.2048, 55.2708)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetLocationScreen(
    listingId: String,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val listings by AuraRepository.listings.collectAsState(initial = emptyList())
    val listing = listings.find { it.id == listingId }
    var geofencePassed by remember { mutableStateOf(false) }
    var liveAddressText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(listingId) {
        if (listing == null) AuraRepository.refreshListingsAwait()
    }

    val lat = listing?.latitude ?: DEFAULT_DUBAI.latitude
    val lng = listing?.longitude ?: DEFAULT_DUBAI.longitude
    // Location Text vs Map Desync fix: prefer reverse-geocoded live location; fallback to listing
    val locationText = liveAddressText ?: listing?.location ?: listing?.emirate ?: "Meetup location"

    // Geofence fix: poll location; "I am here" requires actual proximity (50m for urban canyon)
    val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    LaunchedEffect(hasLocationPermission, lat, lng) {
        if (!hasLocationPermission) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        while (true) {
            val loc = runCatching {
                LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
            }.getOrNull()
            if (loc != null && !loc.isFromMockProvider) {
                val dist = com.aura.app.util.MeetupLocationUtils.distanceMeters(loc, lat, lng)
                geofencePassed = MeetupLocationUtils.isWithinGeofence(dist, useStrictRadius = false)
                // Reverse geocode for live address (Location Text vs Map fix)
                liveAddressText = withContext(Dispatchers.IO) {
                    runCatching {
                        Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
                            ?.firstOrNull()?.getAddressLine(0)
                    }.getOrNull()
                }
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(lat, lng), 14f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Meetup Location", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkVoid,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        containerColor = DarkVoid,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Map API Rate Limit fix (#12): show fallback when map unavailable; address always visible below
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)),
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = com.google.maps.android.compose.MapProperties(
                        isMyLocationEnabled = hasLocationPermission,
                    ),
                    uiSettings = com.google.maps.android.compose.MapUiSettings(
                        zoomControlsEnabled = true,
                        myLocationButtonEnabled = false,
                    ),
                ) {
                    Marker(
                        state = MarkerState(position = LatLng(lat, lng)),
                        title = locationText,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SlateElevated)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Orange500,
                        modifier = Modifier.size(28.dp),
                    )
                    Column {
                        Text(
                            "Meet the seller here",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            locationText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        // Deep Link State Loss fix (#8): persist session before handing off to Maps
                        AuraRepository.currentTradeSession.value?.let { session ->
                            com.aura.app.data.TradeSessionStore.save(context, session)
                        }
                        val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng")
                        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage("com.google.android.apps.maps")
                        }
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        } else {
                            val fallback = Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                            context.startActivity(Intent(Intent.ACTION_VIEW, fallback))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange500),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Open in Google Maps", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Geofence fix (#4): "I am here" requires actual proximity — no couch bypass
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = hasLocationPermission && geofencePassed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Gold500,
                        contentColor = DarkVoid,
                        disabledContainerColor = SlateElevated,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp),
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.size(10.dp))
                    Text(
                        when {
                            !hasLocationPermission -> "Enable location to continue"
                            geofencePassed -> "I am here"
                            else -> "Move within 50m of meetup"
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
