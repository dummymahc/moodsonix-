package iad1tya.echo.music.ui.screens.supabase

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.supabase.models.SupabaseConversation
import iad1tya.echo.music.supabase.models.SupabaseMessage
import iad1tya.echo.music.supabase.models.TypingPresence
import iad1tya.echo.music.viewmodels.MessagingUiState
import iad1tya.echo.music.viewmodels.SupabaseAuthViewModel
import iad1tya.echo.music.viewmodels.SupabaseMessagingViewModel

// =============================================================================
// MessagingScreen.kt
//
// Two composables are exported from this file:
//  1. ConversationListScreen  — shows all conversations the user is part of
//  2. ChatScreen              — the actual one-to-one message thread
//
// Routing (add to your NavGraph):
//   composable("conversations")          { ConversationListScreen(navController) }
//   composable("chat/{partnerId}") { entry ->
//       ChatScreen(navController, entry.arguments?.getString("partnerId") ?: "")
//   }
// =============================================================================

// ─── Conversation List ────────────────────────────────────────────────────────

/**
 * Displays all conversations for the current user.
 * Tapping a row navigates to [ChatScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    navController: NavController,
    messagingViewModel: SupabaseMessagingViewModel = hiltViewModel(),
    authViewModel: SupabaseAuthViewModel           = hiltViewModel(),
) {
    val conversations by messagingViewModel.conversations.collectAsState()
    val uiState       by messagingViewModel.uiState.collectAsState()
    val currentUserId = authViewModel.currentUser.collectAsState().value?.id

    LaunchedEffect(Unit) { messagingViewModel.loadConversations() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState is MessagingUiState.Loading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            conversations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        "No conversations yet.\nSearch for a user to start chatting.",
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(vertical = 8.dp),
                ) {
                    items(conversations, key = { it.id ?: it.participant1Id }) { conv ->
                        ConversationRow(
                            conversation  = conv,
                            currentUserId = currentUserId,
                            onClick       = {
                                val partnerId = if (conv.participant1Id == currentUserId)
                                    conv.participant2Id else conv.participant1Id
                                navController.navigate("chat/$partnerId")
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConversationRow(
    conversation: SupabaseConversation,
    currentUserId: String?,
    onClick: () -> Unit,
) {
    val partnerId = if (conversation.participant1Id == currentUserId)
        conversation.participant2Id else conversation.participant1Id

    Card(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier          = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar placeholder
            Box(
                modifier          = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment  = Alignment.Center,
            ) {
                Text(
                    text  = partnerId.take(2).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = partnerId,   // Replace with profile display_name lookup
                    style    = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = conversation.lastMessageAt?.take(10) ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Chat Screen ──────────────────────────────────────────────────────────────

/**
 * Full-screen one-to-one chat with real-time Supabase Realtime messages and
 * Presence-based typing indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavController,
    partnerUserId: String,
    messagingViewModel: SupabaseMessagingViewModel = hiltViewModel(),
    authViewModel: SupabaseAuthViewModel           = hiltViewModel(),
) {
    val messages      by messagingViewModel.messages.collectAsState()
    val typingUsers   by messagingViewModel.typingUsers.collectAsState()
    val uiState       by messagingViewModel.uiState.collectAsState()
    val currentUserId  = authViewModel.currentUser.collectAsState().value?.id

    val listState = rememberLazyListState()

    // Open the Realtime channel when entering; close it when leaving
    LaunchedEffect(partnerUserId) {
        messagingViewModel.openConversation(partnerUserId)
    }
    DisposableEffect(Unit) {
        onDispose { messagingViewModel.closeCurrentConversation() }
    }

    // Auto-scroll to newest message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState) {
        if (uiState is MessagingUiState.Error) {
            snackbarHostState.showSnackbar((uiState as MessagingUiState.Error).message)
            messagingViewModel.resetState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(partnerUserId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (typingUsers.isNotEmpty()) {
                            Text(
                                text  = "${typingUsers.first().displayName} is typing…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            MessageInputBar(
                onSend    = messagingViewModel::sendMessage,
                onTyping  = messagingViewModel::onUserTyping,
            )
        },
    ) { padding ->
        LazyColumn(
            state          = listState,
            modifier       = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(messages, key = { it.id ?: it.createdAt ?: it.content }) { msg ->
                ChatBubble(
                    message   = msg,
                    isMine    = msg.senderId == currentUserId,
                )
            }
        }
    }
}

// ─── Chat Bubble ──────────────────────────────────────────────────────────────

@Composable
private fun ChatBubble(message: SupabaseMessage, isMine: Boolean) {
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary
                      else        MaterialTheme.colorScheme.surfaceVariant
    val textColor   = if (isMine) MaterialTheme.colorScheme.onPrimary
                      else        MaterialTheme.colorScheme.onSurfaceVariant
    val alignment   = if (isMine) Alignment.End else Alignment.Start

    Column(
        modifier            = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart    = 16.dp,
                        topEnd      = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd   = if (isMine) 4.dp  else 16.dp,
                    )
                )
                .background(bubbleColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = message.content, color = textColor, style = MaterialTheme.typography.bodyMedium)
        }
        // Timestamp + read receipt
        Row(
            horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        ) {
            Text(
                text  = message.createdAt?.take(16)?.replace("T", " ") ?: "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isMine) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = if (message.isRead) "✓✓" else "✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (message.isRead) MaterialTheme.colorScheme.primary
                            else                MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Message Input Bar ────────────────────────────────────────────────────────

@Composable
private fun MessageInputBar(
    onSend: (String) -> Unit,
    onTyping: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    Surface(
        tonalElevation = 3.dp,
        modifier        = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier          = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value         = text,
                onValueChange = {
                    text = it
                    if (it.isNotEmpty()) onTyping()
                },
                placeholder   = { Text("Message…") },
                modifier      = Modifier.weight(1f),
                maxLines      = 4,
                shape         = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (text.isNotBlank()) {
                            onSend(text.trim())
                            text = ""
                        }
                    },
                ),
            )

            Spacer(Modifier.width(8.dp))

            // Send button
            IconButton(
                onClick  = {
                    if (text.isNotBlank()) {
                        onSend(text.trim())
                        text = ""
                    }
                },
                enabled  = text.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (text.isNotBlank()) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector        = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint               = if (text.isNotBlank()) MaterialTheme.colorScheme.onPrimary
                                         else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
