package com.aura.app.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aura.app.data.AuraRepository
import com.aura.app.data.ChatRepository
import com.aura.app.model.ChatMessage
import com.aura.app.navigation.Routes
import com.aura.app.wallet.WalletConnectionState
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onNavigateToChat: (String, String) -> Unit = { _, _ -> },
    onNavigateToHome: () -> Unit = {},
    onNavigateToCreateListing: () -> Unit = {}
) {
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    var activeChats by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Dual-Window Tabs state
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Buying", "Selling")

    LaunchedEffect(walletAddress) {
        isLoading = true
        walletAddress?.let { wallet ->
            activeChats = ChatRepository.getMyConversations(wallet)
        }
        isLoading = false
    }
    
    // Filter chats based on tab
    val buyingChats = remember(activeChats, walletAddress) {
        activeChats.filter { msg ->
            val listing = AuraRepository.getListing(msg.listingId)
            listing?.sellerWallet != walletAddress
        }
    }
    val sellingChats = remember(activeChats, walletAddress) {
        activeChats.filter { msg ->
            val listing = AuraRepository.getListing(msg.listingId)
            listing?.sellerWallet == walletAddress
        }
    }
    
    val currentChats = if (selectedTabIndex == 0) buyingChats else sellingChats

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages", fontWeight = FontWeight.Bold) },
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
            // Sleek, top-level custom Tab switcher with horizontal sliding animation
            CustomTabSwitcher(
                tabs = tabs,
                selectedTabIndex = selectedTabIndex,
                onTabSelected = { selectedTabIndex = it }
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Gold500)
                } else if (currentChats.isEmpty()) {
                    if (selectedTabIndex == 0) {
                        EmptyChatsState(
                            title = "No active conversations",
                            message = "You haven't messaged any sellers yet. Start exploring the marketplace.",
                            buttonText = "Discover Items",
                            buttonIcon = Icons.Default.Search,
                            onClick = onNavigateToHome
                        )
                    } else {
                        EmptyChatsState(
                            title = "No inquiries yet",
                            message = "You have no inquiries. List an item to start receiving messages from buyers.",
                            buttonText = "Create Listing",
                            buttonIcon = Icons.Default.Add,
                            onClick = onNavigateToCreateListing
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(currentChats) { lastMsg ->
                            ChatInboxRow(
                                chatMessage = lastMsg,
                                isBuyingTab = selectedTabIndex == 0,
                                onClick = { 
                                    val counterparty = if (lastMsg.senderWallet == walletAddress) lastMsg.receiverWallet else lastMsg.senderWallet
                                    onNavigateToChat(lastMsg.listingId, counterparty) 
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomTabSwitcher(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(DarkCard)
            .border(1.dp, GlassBorder, RoundedCornerShape(26.dp))
    ) {
        val tabWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * selectedTabIndex,
            animationSpec = tween(durationMillis = 300),
            label = "tab_indicator_offset"
        )

        // Animated Pill Indicator
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Orange500)
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) DarkSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(300),
                    label = "tab_text_color_$index"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInboxRow(chatMessage: ChatMessage, isBuyingTab: Boolean, onClick: () -> Unit) {
    val listing = AuraRepository.getListing(chatMessage.listingId)
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val isMine = chatMessage.senderWallet == walletAddress
    
    val titleUserId = if (isBuyingTab) {
        listing?.sellerWallet ?: "Aura Bot"
    } else {
        if (chatMessage.senderWallet == walletAddress) chatMessage.receiverWallet else chatMessage.senderWallet
    }

    val displayTitle = if (titleUserId.length > 10) {
        "${titleUserId.take(4)}...${titleUserId.takeLast(4)}"
    } else {
        titleUserId
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .shadow(
                elevation = 8.dp, 
                shape = RoundedCornerShape(16.dp),
                spotColor = Orange500.copy(alpha = 0.2f), 
                ambientColor = Orange500.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Premium Avatar Placeholder
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(DarkCard)
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            val imageUrl = listing?.images?.firstOrNull()
            if (imageUrl != null) {
                AsyncImage(
                    model = if (imageUrl.startsWith("http")) imageUrl else "file://$imageUrl",
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = null, tint = Orange500)
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically, 
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = runCatching {
                        val dt = OffsetDateTime.parse(chatMessage.createdAt)
                        val now = OffsetDateTime.now()
                        if (dt.toLocalDate() == now.toLocalDate()) {
                            dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                        } else {
                            dt.format(DateTimeFormatter.ofPattern("MMM dd"))
                        }
                    }.getOrDefault(""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            
            // Subtitle emphasizing the product name
            Text(
                text = listing?.title ?: "Listing Item",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Gold500,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // Latest Message Preview
            Text(
                text = buildString {
                    if (isMine) append("You: ")
                    append(chatMessage.content)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyChatsState(
    title: String,
    message: String,
    buttonText: String,
    buttonIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(GlassSurface)
                .border(2.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = "No Messages",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.5f),
                modifier = Modifier.size(56.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Gold500),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(buttonIcon, contentDescription = null, tint = DarkSurface)
            Spacer(modifier = Modifier.padding(4.dp))
            Text(buttonText, fontWeight = FontWeight.Bold, color = DarkSurface)
        }
    }
}
