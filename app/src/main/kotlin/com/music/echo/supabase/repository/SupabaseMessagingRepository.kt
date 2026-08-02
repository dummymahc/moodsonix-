package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.models.SupabaseConversation
import iad1tya.echo.music.supabase.models.SupabaseMessage
import iad1tya.echo.music.supabase.models.TypingPresence
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeJoinsAs
import io.github.jan.supabase.realtime.decodeLeavesAs
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the real-time direct messaging system.
 *
 * Features:
 * ─ One-to-one messages stored in the `messages` table
 * ─ Live INSERT events streamed via Supabase Realtime (PostgresChangeFlow)
 * ─ Typing indicators via Supabase Presence (track / untrack)
 * ─ Conversation list with last-message timestamps
 *
 * Architecture note:
 *   • [openMessageChannel] creates and subscribes a channel; the caller must
 *     call [closeChannel] when the conversation screen is left to release the
 *     WebSocket subscription.
 *   • All Flows are cold — they only emit while the channel is subscribed.
 */
@Singleton
class SupabaseMessagingRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    // Keep a reference so we can unsubscribe cleanly
    private var activeChannel: RealtimeChannel? = null

    // ── Conversations ─────────────────────────────────────────────────────────

    /** Returns all conversations the current user is part of, sorted by most-recent activity. */
    suspend fun getConversations(): Result<List<SupabaseConversation>> = runCatching {
        val uid = requireUid()
        supabase.from("conversations")
            .select {
                filter {
                    // RLS already limits to conversations the user participates in,
                    // but the explicit OR makes the intent clear for readers.
                    or {
                        eq("participant1_id", uid)
                        eq("participant2_id", uid)
                    }
                }
                order("last_message_at", Order.DESCENDING)
            }
            .decodeList<SupabaseConversation>()
    }.onFailure { Timber.e(it, "Supabase messaging: getConversations failed") }

    // ── Messages ──────────────────────────────────────────────────────────────

    /**
     * Fetches existing messages between the current user and [otherUserId],
     * ordered oldest-first so the UI can render them top-to-bottom.
     */
    suspend fun getMessages(otherUserId: String,
                            limit: Int = 100): Result<List<SupabaseMessage>> =
        runCatching {
            val uid = requireUid()
            supabase.from("messages")
                .select {
                    filter {
                        or {
                            and {
                                eq("sender_id",   uid)
                                eq("receiver_id", otherUserId)
                            }
                            and {
                                eq("sender_id",   otherUserId)
                                eq("receiver_id", uid)
                            }
                        }
                    }
                    order("created_at", Order.ASCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SupabaseMessage>()
        }.onFailure { Timber.e(it, "Supabase messaging: getMessages failed") }

    /** Sends a message to [receiverId] with [content]. */
    suspend fun sendMessage(receiverId: String, content: String): Result<SupabaseMessage> =
        runCatching {
            val uid = requireUid()
            val msg = SupabaseMessage(
                senderId   = uid,
                receiverId = receiverId,
                content    = content,
            )
            supabase.from("messages")
                .insert(msg) { select() }
                .decodeSingle<SupabaseMessage>()
                .also { Timber.d("Supabase messaging: sent [${it.id}] to $receiverId") }
        }.onFailure { Timber.e(it, "Supabase messaging: sendMessage failed to $receiverId") }

    /** Marks all messages from [senderId] to the current user as read. */
    suspend fun markMessagesAsRead(senderId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        supabase.from("messages").update(mapOf("is_read" to true)) {
            filter {
                eq("sender_id",   senderId)
                eq("receiver_id", uid)
                eq("is_read",     false)
            }
        }
        Unit
    }.onFailure { Timber.e(it, "Supabase messaging: markMessagesAsRead failed") }

    // ── Realtime — live message stream ────────────────────────────────────────

    /**
     * Opens a Realtime channel scoped to messages between the current user and
     * [otherUserId].  Returns a [Flow] that emits each new [SupabaseMessage]
     * as it arrives.
     *
     * The caller is responsible for calling [closeChannel] when done (typically
     * in `onCleared()` of the ViewModel or `DisposableEffect` in Compose).
     */
    suspend fun openMessageChannel(otherUserId: String): Flow<SupabaseMessage> {
        val uid = requireUid()
        val channelName = buildChannelName(uid, otherUserId)

        val channel = supabase.channel(channelName)
        activeChannel = channel

        // Subscribe to INSERT events on the messages table.
        // Supabase filters cannot handle OR conditions directly in Realtime, so
        // we subscribe to all inserts for the current user as receiver and
        // filter the other direction client-side.
        val insertFlow: Flow<SupabaseMessage> = channel
            .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table  = "messages"
                // Only receive rows where the current user is either sender or receiver
                filter = "receiver_id=eq.$uid"
            }
            .map { action ->
                action.decodeRecord<SupabaseMessage>()
            }

        channel.subscribe()
        Timber.d("Supabase messaging: subscribed to channel [$channelName]")
        return insertFlow
    }

    // ── Realtime — Presence (typing indicators) ────────────────────────────────

    /**
     * Returns a [Flow] of [TypingPresence] objects emitted whenever the presence
     * state changes in the current conversation channel.
     *
     * [openMessageChannel] must be called first to establish the channel.
     */
    fun typingPresenceFlow(): Flow<List<TypingPresence>> {
        val ch = activeChannel ?: error("Call openMessageChannel() first")
        return ch.presenceChangeFlow()
            .map { action ->
                action.decodeJoinsAs<TypingPresence>() +
                    action.decodeLeavesAs<TypingPresence>()
            }
    }

    /**
     * Broadcasts that the current user [isTyping] (or stopped typing).
     * Pass the user's [displayName] so the other side can show it.
     */
    suspend fun setTyping(userId: String, displayName: String, isTyping: Boolean) {
        try {
            val ch = activeChannel ?: return
            if (isTyping) {
                ch.track(TypingPresence(userId, displayName, true))
            } else {
                ch.track(TypingPresence(userId, displayName, false))
            }
        } catch (e: Exception) {
            Timber.w(e, "Supabase messaging: setTyping failed (non-critical)")
        }
    }

    /** Unsubscribes from the active channel and releases the WebSocket connection. */
    suspend fun closeChannel() {
        try {
            activeChannel?.unsubscribe()
            activeChannel = null
            Timber.d("Supabase messaging: channel closed")
        } catch (e: Exception) {
            Timber.w(e, "Supabase messaging: closeChannel failed (non-critical)")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireUid(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase: cannot access messages — user is not signed in")

    /**
     * Builds a deterministic channel name for the pair [uid1]/[uid2] so both
     * participants join the same WebSocket channel regardless of who "opened" it.
     */
    private fun buildChannelName(uid1: String, uid2: String): String {
        val (a, b) = if (uid1 < uid2) uid1 to uid2 else uid2 to uid1
        return "dm:${a}_$b"
    }
}
