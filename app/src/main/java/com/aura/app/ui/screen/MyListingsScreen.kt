package com.aura.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.model.Listing
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import kotlinx.coroutines.launch
import java.text.DecimalFormat

private val SOL_FORMATTER = DecimalFormat("0.####")
private fun lamportsToSol(lamports: Long) = lamports / 1_000_000_000.0
private fun solToLamports(sol: Double) = (sol * 1_000_000_000).toLong()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListingsScreen(onBack: () -> Unit) {
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var listings by remember { mutableStateOf<List<Listing>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Edit Price Dialog state
    var editingListing by remember { mutableStateOf<Listing?>(null) }
    var priceInput by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveSuccess by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(walletAddress) {
        isLoading = true
        walletAddress?.let { wallet ->
            listings = AuraRepository.fetchUserListings(wallet)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Listings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkSurface
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator(color = Gold500)

                listings.isEmpty() -> {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No active listings",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You haven't listed anything yet. Head to Quick Actions → Create Listing.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(listings, key = { it.id }) { listing ->
                            MyListingCard(
                                listing = listing,
                                onEditPrice = {
                                    editingListing = listing
                                    priceInput = SOL_FORMATTER.format(lamportsToSol(listing.priceLamports))
                                    saveSuccess = false
                                    saveError = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Edit Price AlertDialog ──
    editingListing?.let { listing ->
        AlertDialog(
            onDismissRequest = {
                if (!isSaving) {
                    editingListing = null
                    saveSuccess = false
                    saveError = null
                }
            },
            containerColor = DarkCard,
            title = {
                Text(
                    "Edit Price",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = listing.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Gold500,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = {
                            priceInput = it
                            saveSuccess = false
                            saveError = null
                        },
                        label = { Text("New Price (SOL)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Orange500,
                            focusedLabelColor = Orange500,
                            cursorColor = Orange500
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (saveSuccess) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Price updated!", color = androidx.compose.ui.graphics.Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    saveError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newSol = priceInput.toDoubleOrNull()
                        if (newSol == null || newSol <= 0.0) {
                            saveError = "Please enter a valid price in SOL."
                            return@Button
                        }
                        val newLamports = solToLamports(newSol)
                        isSaving = true
                        scope.launch {
                            val success = AuraRepository.updateListingPrice(listing.id, newLamports)
                            if (success) {
                                // Update local list state to reflect new price
                                listings = listings.map {
                                    if (it.id == listing.id) it.copy(priceLamports = newLamports) else it
                                }
                                saveSuccess = true
                                saveError = null
                            } else {
                                saveError = "Failed to update. Please try again."
                            }
                            isSaving = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Orange500),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DarkSurface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save", fontWeight = FontWeight.Bold, color = DarkSurface)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        editingListing = null
                        saveSuccess = false
                        saveError = null
                    },
                    enabled = !isSaving
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun MyListingCard(listing: Listing, onEditPrice: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Orange500.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(4.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(DarkCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            val imageUrl = listing.images.firstOrNull()
            if (imageUrl != null) {
                AsyncImage(
                    model = if (imageUrl.startsWith("http")) imageUrl else "file://$imageUrl",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.List, contentDescription = null, tint = Orange500)
            }
        }

        // Details
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = listing.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listing.condition,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${SOL_FORMATTER.format(lamportsToSol(listing.priceLamports))} SOL",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = Gold500,
                fontSize = 15.sp
            )
        }

        // Edit Price Button
        OutlinedButton(
            onClick = onEditPrice,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Orange500.copy(alpha = 0.7f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange500),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = "Edit Price", modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Edit Price", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
