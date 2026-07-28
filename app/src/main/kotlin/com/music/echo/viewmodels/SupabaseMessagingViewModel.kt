package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.supabase.models.SupabaseConversation
import iad1tya.echo.music.supabase.models.SupabaseMessage
import iad1tya.echo.music.supabase.models.TypingPresence
import iad1tya.echo.music.supabase.repository.SupabaseAuthRepository
import iad1tya.echo.music.supabase.repository.SupabaseMessagingRepository
import iad1tya.echo.music.supabase.repository.SupabaseProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ─── UI States ────────────────────────────────────────────────────────────────

sealed class MessagingUiState {
    object Idle        : MessagingUiState()
    object Loading     : MessagingUiState()
    object Success     : MessagingUiState()
    data class Error(val message: String) : MessagingUiState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * ViewModel for the real-time direct messaging feature.
 *
 * Lifecycle:
 * 1. Call [loadConversations] when the conversation-list screen opens.
 * 2. Call [openConversation] when the user taps a conversation row.
 *    This subscribes the Realtime channel and starts pumping [messages].
 * 3. Call [sendMessage] to post a new message.
 * 4. Call [setTyping] as the user types (debounced to 2 s).
 * 5. [onCleared] automatically closes the Realtime channel.
 *
 * MVVM wiring:
 * - Every [StateFlow] is observed with `collectAsState()` in Compose screens.
 * - Business logic stays here; screens stay thin.
 */
@HiltViewModel
class SupabaseMessagingViewModel @Inject constructor(
    private val messagingRepository: SupabaseMessagingRepository,
    private val authRepository: SupabaseAuthRepository,
    private val profileRepository: SupabaseProfileRepository,
) : ViewModel() {

    // ── Exposed state ──────────────────────────────────────────────────────────

    private val _uiState = MutableStateFlow<MessagingUiState>(MessagingUiState.Idle)
    val uiState: StateFlow<MessagingUiState> = _uiState.asStateFlow()

    /** All conversations the current user is part of. */
    private val _conversations = MutableStateFlow<List<SupabaseConversation>>(emptyList())
    val conversations: StateFlow<List<SupabaseConversation>> = _conversations.asStateFlow()

    /** Messages in the currently open conversation (oldest first). */
    private val _messages = MutableStateFlow<List<SupabaseMessage>>(emptyList())
    val messages: StateFlow<List<SupabaseMessage>> = _messages.asStateFlow()

    /** Users who are currently typing in the open conversation. */
    private val _typingUsers = MutableStateFlow<List<TypingPresence>>(emptyList())
    val typingUsers: StateFlow<List<TypingPresence>> = _typingUsers.asStateFlow()

    /** The userId of the person we're currently chatting with. */
    private val _activeConversationPartnerId = MutableStateFlow<String?>(null)
    val activeConversationPartnerId: StateFlow<String?> = _activeConversationPartnerId.asStateFlow()

    // ── Internal ───────────────────────────────────────────────────────────────

    /** Coroutine collecting live Realtime inserts — cancelled when conversation closes. */
    private var realtimeJob: Job? = null

    /** Coroutine collecting presence changes (typing indicators). */
    private var presenceJob: Job? = null

    /** Job used to auto-clear the "typing" indicator after a short idle period. */
    private var typingStopJob: Job? = null

    // ── Conversations ──────────────────────────────────────────────────────────

    /** Fetches (or refreshes) the list of conversations for the current user. */
    fun loadConversations() {
        viewModelScope.launch {
            _uiState.value = MessagingUiState.Loading
            messagingRepository.getConversations()
                .onSuccess { list ->
                    _conversations.value = list
                    _uiState.value       = MessagingUiState.Success
                }
                .onFailure { e ->
                    _uiState.value = MessagingUiState.Error(
                        e.message ?: "Failed to load conversations"
                    )
                    Timber.e(e, "SupabaseMessagingViewModel: loadConversations failed")
                }
        }
    }

    // ── Open a conversation ────────────────────────────────────────────────────

    /**
     * Opens the conversation with [partnerUserId]:
     * 1. Loads historical messages from Postgres.
     * 2. Subscribes to Supabase Realtime for live inserts.
     * 3. Subscribes to Presence for typing indicators.
     * 4. Marks existing messages as read.
     */
    fun openConversation(partnerUserId: String) {
        // Close any previously open conversation first
        closeCurrentConversation()

        _activeConversationPartnerId.value = partnerUserId
        _uiState.value = MessagingUiState.Loading

        viewModelScope.launch {
            // 1. Fetch history
            messagingRepository.getMessages(partnerUserId)
                .onSuccess { history ->
                    _messages.value = history
                    _uiState.value  = MessagingUiState.Success
                }
                .onFailure { e ->
                    _uiState.value = MessagingUiState.Error(
                        e.message ?: "Failed to load messages"
                    )
                    Timber.e(e, "SupabaseMessagingViewModel: getMessages failed")
                    return@launch
                }

            // 2. Subscribe to Realtime inserts
            try {
                val flow = messagingRepository.openMessageChannel(partnerUserId)
                realtimeJob = viewModelScope.launch {
                    flow.collect { newMsg ->
                        // Append only if not already in the list (de-dup guard)
                        if (_messages.value.none { it.id == newMsg.id }) {
                            _messages.value = _messages.value + newMsg
                        }
                        // Auto-mark incoming messages as read
                        if (newMsg.senderId == partnerUserId) {
                            messagingRepository.markMessagesAsRead(partnerUserId)
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "SupabaseMessagingViewModel: Realtime subscription failed")
            }

            // 3. Collect typing presence
            try {
                presenceJob = viewModelScope.launch {
                    messagingRepository.typingPresenceFlow().collect { presences ->
                        // Filter out the current user so we don't show "you are typing"
                        val myId = authRepository.currentUserId()
                        _typingUsers.value = presences.filter { it.userId != myId }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "SupabaseMessagingViewModel: Presence collection failed (non-critical)")
            }

            // 4. Mark existing messages as read
            messagingRepository.markMessagesAsRead(partnerUserId)
        }
    }

    // ── Send ──────────────────────────────────────────────────────────────────

    /** Sends [content] to the currently open conversation partner. */
    fun sendMessage(content: String) {
        val partnerId = _activeConversationPartnerId.value ?: return
        if (content.isBlank()) return

        viewModelScope.launch {
            // Stop typing indicator immediately when the user sends
            stopTyping()

            messagingRepository.sendMessage(partnerId, content)
                .onSuccess { sentMsg ->
                    // Optimistically add to the local list if Realtime hasn't fired yet
                    if (_messages.value.none { it.id == sentMsg.id }) {
                        _messages.value = _messages.value + sentMsg
                    }
                }
                .onFailure { e ->
                    _uiState.value = MessagingUiState.Error(
                        e.message ?: "Failed to send message"
                    )
                    Timber.e(e, "SupabaseMessagingViewModel: sendMessage failed")
                }
        }
    }

    // ── Typing indicators ─────────────────────────────────────────────────────

    /**
     * Call this whenever the user types a character.
     * Broadcasts "is_typing = true" and schedules an automatic stop after 2 s
     * of inactivity — this way we never leave a stale typing indicator up if
     * the user abandons the input field.
     */
    fun onUserTyping() {
        val uid  = authRepository.currentUserId() ?: return
        val name = "User"   // Could be replaced with the profile's displayName

        viewModelScope.launch {
            messagingRepository.setTyping(uid, name, isTyping = true)
        }

        // Debounce: cancel any pending stop and schedule a fresh one
        typingStopJob?.cancel()
        typingStopJob = viewModelScope.launch {
            delay(2_000L)
            stopTyping()
        }
    }

    private fun stopTyping() {
        val uid  = authRepository.currentUserId() ?: return
        val name = "User"
        viewModelScope.launch {
            messagingRepository.setTyping(uid, name, isTyping = false)
        }
    }

    // ── Close conversation ────────────────────────────────────────────────────

    /** Closes the Realtime channel; call when leaving the conversation screen. */
    fun closeCurrentConversation() {
        realtimeJob?.cancel()
        presenceJob?.cancel()
        typingStopJob?.cancel()
        realtimeJob  = null
        presenceJob  = null
        typingStopJob = null
        _activeConversationPartnerId.value = null
        _messages.value    = emptyList()
        _typingUsers.value = emptyList()
        viewModelScope.launch {
            messagingRepository.closeChannel()
        }
    }

    /** Resets [uiState] to Idle (e.g. after the UI has shown an error Snackbar). */
    fun resetState() { _uiState.value = MessagingUiState.Idle }

    override fun onCleared() {
        super.onCleared()
        // Ensure the WebSocket is released when the ViewModel is destroyed
        viewModelScope.launch { messagingRepository.closeChannel() }
    }
}
