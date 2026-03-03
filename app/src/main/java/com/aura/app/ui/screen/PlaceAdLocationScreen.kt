package com.aura.app.ui.screen

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.Orange500
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.rememberCoroutineScope
import com.aura.app.data.AuraRepository
import com.aura.app.data.SupabaseClient
import com.aura.app.wallet.WalletConnectionState
import io.github.jan.supabase.storage.storage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceAdLocationScreen(
    onLocationConfirmed: () -> Unit,
    onBack: () -> Unit
) {
    val emirates = listOf("Dubai", "Abu Dhabi", "Sharjah", "Ajman", "Umm Al Quwain", "Ras Al Khaimah", "Fujairah")
    var selectedEmirate by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Emirate") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkSurface
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Where should we place your ad?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Manual Selection List
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(emirates) { emirate ->
                    val isSelected = selectedEmirate == emirate
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                            .background(if (isSelected) Orange500.copy(alpha=0.2f) else com.aura.app.ui.theme.DarkCard)
                            .border(
                                2.dp,
                                if (isSelected) Orange500 else com.aura.app.ui.theme.GlassBorder,
                                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedEmirate = emirate }
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = if (isSelected) Orange500 else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = emirate,
                                color = if (isSelected) Orange500 else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }

            // Bottom Confirmation Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(24.dp)
            ) {
                Button(
                    onClick = {
                        val uri = AdCreationState.selectedImageUri
                        val category = AdCreationState.category.ifBlank { "Item" }
                        val wallet = WalletConnectionState.walletAddress.value
                        val emirate = selectedEmirate

                        if (uri != null && wallet != null && emirate != null) {
                            scope.launch {
                                try {
                                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                    if (bytes != null) {
                                        val fileName = "listing_${System.currentTimeMillis()}.jpg"
                                        val bucket = SupabaseClient.client.storage["listing-images"]
                                        bucket.upload(fileName, bytes)
                                        val publicUrl = bucket.publicUrl(fileName)
                                        
                                        AuraRepository.createListing(
                                            sellerWallet = wallet,
                                            title = "$category - AI Crafted ($emirate)",
                                            priceLamports = 50_000_000_000L,
                                            imageRefs = listOf(publicUrl),
                                            condition = "Pristine",
                                            textureHash = "ai_hash_${System.currentTimeMillis()}"
                                        )
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    AdCreationState.clear()
                                    onLocationConfirmed()
                                }
                            }
                        } else {
                            AdCreationState.clear()
                            onLocationConfirmed()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = com.aura.app.ui.theme.Gold500),
                    enabled = selectedEmirate != null
                ) {
                    Text(
                        "Confirm Location", 
                        color = com.aura.app.ui.theme.DarkSurface, 
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
