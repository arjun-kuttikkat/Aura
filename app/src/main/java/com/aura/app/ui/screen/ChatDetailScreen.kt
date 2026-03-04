package com.aura.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.aura.app.data.AiChatResponder
import com.aura.app.data.AuraRepository
import com.aura.app.data.ChatRepository
import com.aura.app.model.ChatMessage
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.GlassSurface
import com.aura.app.ui.theme.Gold500
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SolanaGreen
import com.aura.app.ui.theme.UltraViolet
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
    val isOfficialBot = listing?.sellerWallet == AiChatResponder.AURA_OFFICIAL_WALLET

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isAiTyping by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 1. Initial Load
    LaunchedEffect(listingId) {
        messages = ChatRepository.getMessagesForListing(listingId)
        // For official bot: send welcome message if first time
        if (isOfficialBot && messages.isEmpty()) {
            isAiTyping = true
            listing?.let {
                val welcome = AiChatResponder.generateReply(it, emptyList())
                val welcomeMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    listingId = listingId,
                    senderWallet = AiChatResponder.AURA_OFFICIAL_WALLET,
                    receiverWallet = walletAddress ?: "guest",
                    content = welcome
                )
                ChatRepository.sendMessage(welcomeMsg)
                messages = listOf(welcomeMsg)
            }
            isAiTyping = false
        }
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 2. Realtime Subscription (for human-to-human chats)
    LaunchedEffect(listingId) {
        if (!isOfficialBot) {
            ChatRepository.observeMessages(listingId).collect { newMessage ->
                if (messages.none { it.id == newMessage.id }) {
                    messages = messages + newMessage
                    listState.animateScrollToItem(messages.size - 1)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isOfficialBot) "🌟 Aura Wellness" else "Chat with Seller",
                            fontWeight = FontWeight.Bold, fontSize = 18.sp
                        )
                        listing?.title?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOfficialBot) SolanaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
            // Bot header banner
            if (isOfficialBot) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UltraViolet.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("💙", fontSize = 18.sp)
                    Text(
                        "Aura AI • Mood Tracker & Wellness Guide",
                        style = MaterialTheme.typography.labelMedium,
                        color = SolanaGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { msg ->
                    val isMine = msg.senderWallet == walletAddress
                    ChatBubble(msg = msg, isMine = isMine, isBot = isOfficialBot && !isMine)
                }
                if (isAiTyping) {
                    item {
                        AiTypingIndicator()
                    }
                }
            }

            // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    placeholder = {
                        Text(
                            if (isOfficialBot) "Share how you're feeling..." else "Type a message..."
                        )
                    },
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
                            val userMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                listingId = listingId,
                                senderWallet = walletAddress!!,
                                receiverWallet = listing.sellerWallet,
                                content = inputText.trim()
                            )
                            val text = inputText.trim()
                            inputText = ""
                            messages = messages + userMsg
                            scope.launch {
                                ChatRepository.sendMessage(userMsg)
                                // Auto-reply for official bot
                                if (isOfficialBot) {
                                    isAiTyping = true
                                    listState.animateScrollToItem(messages.size - 1)
                                    val reply = AiChatResponder.generateReply(listing, messages)
                                    val botMsg = ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        listingId = listingId,
                                        senderWallet = AiChatResponder.AURA_OFFICIAL_WALLET,
                                        receiverWallet = walletAddress!!,
                                        content = reply
                                    )
                                    ChatRepository.sendMessage(botMsg)
                                    messages = messages + botMsg
                                    isAiTyping = false
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .background(if (isOfficialBot) UltraViolet else Orange500, CircleShape)
                        .size(48.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AiTypingIndicator() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 80.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp))
                .background(DarkCard)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("💙 Thinking...", color = SolanaGreen, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, isMine: Boolean, isBot: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isMine) 48.dp else 0.dp,
                end = if (isMine) 0.dp else 48.dp
            ),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp
                    )
                )
                .background(
                    when {
                        isMine -> Orange500
                        isBot -> UltraViolet.copy(alpha = 0.3f)
                        else -> DarkCard
                    }
                )
                .padding(12.dp)
        ) {
            Text(
                text = msg.content,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}


