package com.aura.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.app.data.AuraRepository
import com.aura.app.data.ChatRepository
import com.aura.app.model.ChatMessage
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.GlassBorder
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    listingId: String,
    onBack: () -> Unit
) {
    val walletAddress by WalletConnectionState.walletAddress.collectAsState()
    val listing = AuraRepository.getListing(listingId)
    
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // 1. Initial Load
    LaunchedEffect(listingId) {
        messages = ChatRepository.getMessagesForListing(listingId)
    }

    // 2. Realtime Subscription
    LaunchedEffect(listingId) {
        ChatRepository.observeMessages(listingId).collect { newMessage ->
            // Prevent duplicates
            if (messages.none { it.id == newMessage.id }) {
                messages = messages + newMessage
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("Chat with Seller", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        listing?.title?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                reverseLayout = false // In a real app we'd reverse this and push new to bottom 
            ) {
                items(messages) { msg ->
                    val isMine = msg.senderWallet == walletAddress
                    ChatBubble(msg = msg, isMine = isMine)
                }
            }

            // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = { Text("Type a message...") },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                IconButton(
                    onClick = {
                        if (inputText.isNotBlank() && walletAddress != null && listing != null) {
                            val newMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                listingId = listingId,
                                senderWallet = walletAddress!!,
                                receiverWallet = listing.sellerWallet,
                                content = inputText.trim()
                            )
                            scope.launch {
                                ChatRepository.sendMessage(newMsg)
                                // Optimistic update
                                messages = messages + newMsg
                                inputText = ""
                            }
                        }
                    },
                    modifier = Modifier
                        .background(Orange500, CircleShape)
                        .size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, isMine: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isMine) 16.dp else 4.dp,
                    bottomEnd = if (isMine) 4.dp else 16.dp
                ))
                .background(if (isMine) Orange500 else DarkCard)
                .padding(12.dp)
        ) {
            Text(
                text = msg.content,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
