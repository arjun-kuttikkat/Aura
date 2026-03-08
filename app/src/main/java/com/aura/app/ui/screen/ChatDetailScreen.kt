package com.aura.app.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aura.app.data.AiChatResponder
import com.aura.app.data.AuraRepository
import com.aura.app.data.ChatRepository
import com.aura.app.model.ChatMessage
import com.aura.app.ui.theme.DarkCard
import com.aura.app.ui.theme.DarkSurface
import com.aura.app.ui.theme.Orange500
import com.aura.app.ui.theme.SolanaGreen
import com.aura.app.ui.theme.UltraViolet
import com.aura.app.ui.components.GlassCardDark
import com.aura.app.ui.components.RequestLocationTimingSheet
import com.aura.app.wallet.WalletConnectionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

private const val MEETUP_CONFIRMED = "Confirmed"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    listingId: String,
    onBack: () -> Unit,
    onConfirmMeetupPlan: (() -> Unit)? = null,
) {
    val walletAddress by WalletConnectionState.walletAddress.collectAsState(initial = null)
    val listing = AuraRepository.getListing(listingId)
    val isOfficialBot = listing?.sellerWallet == AiChatResponder.AURA_OFFICIAL_WALLET

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var isAiTyping by remember { mutableStateOf(false) }
    var showPlanMeetup by remember { mutableStateOf(false) }
    var helpClicked by remember { mutableStateOf(false) }
    var showLocationTimingSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && walletAddress != null && listing != null) {
            val message = ChatMessage(
                id = UUID.randomUUID().toString(),
                listingId = listingId,
                senderWallet = walletAddress!!,
                receiverWallet = listing.sellerWallet,
                content = "Shared an image",
                imageUrl = uri.toString(),
            )
            messages = messages + message
            scope.launch {
                ChatRepository.sendMessage(message)
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    // 1. Initial Load
    LaunchedEffect(listingId) {
        messages = ChatRepository.getMessagesForListing(listingId)
        walletAddress?.let { ChatRepository.markConversationAsRead(listingId, it) }
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
                    walletAddress?.let { ChatRepository.markConversationAsRead(listingId, it) }
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
                            if (isOfficialBot) "Aura Wellness" else "Buyer / Seller Chat",
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
                actions = {
                    if (!isOfficialBot && listing != null && onConfirmMeetupPlan != null) {
                        IconButton(onClick = { helpClicked = true }) {
                            Text("Help", fontSize = 12.sp, color = Orange500)
                        }
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
            // Plan Meetup (buyer/seller chat only)
            if (!isOfficialBot && listing != null && onConfirmMeetupPlan != null) {
                val hasMeetupRequest = messages.any { it.senderWallet != listing.sellerWallet && it.content.contains("📍 Meetup requested:") }
                val hasSellerConfirmed = messages.any { it.senderWallet == listing.sellerWallet && it.content == MEETUP_CONFIRMED }
                val isSeller = walletAddress == listing.sellerWallet
                val confirmMeetupEnabled = walletAddress != null && hasMeetupRequest && (hasSellerConfirmed || helpClicked)

                GlassCardDark(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    glowColor = Orange500,
                    cornerRadius = 16.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Plan Meetup", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { showPlanMeetup = !showPlanMeetup }) {
                                    Text(if (showPlanMeetup) "Collapse" else "Expand", color = Orange500)
                                }
                            }
                        }
                        if (showPlanMeetup) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                listing.location ?: listing.emirate ?: "Meetup location",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Agree on date & time in chat, then confirm below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Buyer: request location & time
                            if (!isSeller) {
                                OutlinedButton(
                                    onClick = { showLocationTimingSheet = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Orange500),
                                    border = BorderStroke(1.dp, Orange500.copy(alpha = 0.6f)),
                                ) {
                                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Request your preferred location and timing", fontWeight = FontWeight.Medium)
                                }
                            }
                            // Seller: confirm meetup when buyer requested
                            if (isSeller && hasMeetupRequest && !hasSellerConfirmed) {
                                Button(
                                    onClick = {
                                        walletAddress?.let { wallet ->
                                            val msg = ChatMessage(
                                                id = UUID.randomUUID().toString(),
                                                listingId = listingId,
                                                senderWallet = wallet,
                                                receiverWallet = messages.find { it.content.contains("📍 Meetup requested:") }?.senderWallet ?: "",
                                                content = MEETUP_CONFIRMED,
                                            )
                                            messages = messages + msg
                                            scope.launch {
                                                ChatRepository.sendMessage(msg)
                                                listState.animateScrollToItem(messages.size - 1)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = SolanaGreen, contentColor = Color.Black),
                                    enabled = walletAddress != null,
                                ) {
                                    Text("Confirm Meetup", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            // Confirm Meetup Plan: only show after buyer requested AND seller confirmed
                            if (hasMeetupRequest) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        walletAddress?.let { wallet ->
                                            scope.launch {
                                                AuraRepository.createTradeSession(
                                                    listingId = listingId,
                                                    buyerWallet = wallet,
                                                    sellerWallet = listing.sellerWallet,
                                                )
                                                delay(150)
                                                onConfirmMeetupPlan()
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Orange500, contentColor = Color.Black),
                                    enabled = confirmMeetupEnabled,
                                ) {
                                    Text(
                                        if (confirmMeetupEnabled) "Start Meetup" else "Waiting for seller to confirm...",
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

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
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderWallet == walletAddress
                    val role = when {
                        isOfficialBot && !isMine -> "Aura"
                        listing != null && msg.senderWallet == listing.sellerWallet -> "Seller"
                        else -> "Buyer"
                    }
                    ChatBubble(
                        msg = msg,
                        isMine = isMine,
                        isBot = isOfficialBot && !isMine,
                        roleLabel = role,
                        showReadReceipt = isMine,
                    )
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
                    onClick = { imagePickerLauncher.launch("image/*") },
                    enabled = walletAddress != null && listing != null,
                ) {
                    Icon(Icons.Default.Image, contentDescription = "Share image", tint = Orange500)
                }

                val canSend = inputText.isNotBlank() && walletAddress != null && listing != null
                IconButton(
                    onClick = {
                        if (canSend) {
                            val text = inputText.trim()
                            val userMsg = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                listingId = listingId,
                                senderWallet = walletAddress!!,
                                receiverWallet = listing!!.sellerWallet,
                                content = text,
                            )
                            inputText = ""
                            messages = messages + userMsg
                            scope.launch {
                                ChatRepository.sendMessage(userMsg)
                                if (isOfficialBot) {
                                    isAiTyping = true
                                    listState.animateScrollToItem(messages.size - 1)
                                    val reply = AiChatResponder.generateReply(listing!!, messages)
                                    val botMsg = ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        listingId = listingId,
                                        senderWallet = AiChatResponder.AURA_OFFICIAL_WALLET,
                                        receiverWallet = walletAddress!!,
                                        content = reply,
                                    )
                                    ChatRepository.sendMessage(botMsg)
                                    messages = messages + botMsg
                                    isAiTyping = false
                                    listState.animateScrollToItem(messages.size - 1)
                                } else {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        }
                    },
                    enabled = canSend,
                    modifier = Modifier
                        .background(
                            if (!canSend) Color.Gray.copy(alpha = 0.3f)
                            else if (isOfficialBot) UltraViolet else Orange500,
                            CircleShape
                        )
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

    if (showLocationTimingSheet && listing != null) {
        RequestLocationTimingSheet(
            listingId = listing.id,
            onDismiss = { showLocationTimingSheet = false },
            onSaved = { address, time ->
                showLocationTimingSheet = false
                walletAddress?.let { wallet ->
                    val msg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        listingId = listingId,
                        senderWallet = wallet,
                        receiverWallet = listing.sellerWallet,
                        content = "📍 Meetup requested: $address at $time. Seller, please confirm.",
                    )
                    scope.launch {
                        ChatRepository.sendMessage(msg)
                        messages = messages + msg
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            },
        )
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
fun ChatBubble(
    msg: ChatMessage,
    isMine: Boolean,
    isBot: Boolean,
    roleLabel: String,
    showReadReceipt: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (isMine) 48.dp else 0.dp,
                end = if (isMine) 0.dp else 48.dp
            ),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(if (isBot) "Aura" else roleLabel, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        disabledContainerColor = if (isMine) Orange500.copy(alpha = 0.2f) else DarkCard,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurface,
                    )
                )
                Text(
                    text = formatChatTime(msg.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                Column(horizontalAlignment = if (isMine) Alignment.End else Alignment.Start) {
                    if (!msg.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = msg.imageUrl,
                            contentDescription = "Shared image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = msg.content,
                        color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start,
                    )
                }
            }
            if (showReadReceipt) {
                Text(
                    text = if (msg.isRead) "Read" else "Delivered",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, end = 2.dp),
                )
            }
        }
    }
}

private fun formatChatTime(iso: String): String {
    return runCatching {
        val odt = OffsetDateTime.parse(iso)
        odt.format(DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrDefault("Now")
}


