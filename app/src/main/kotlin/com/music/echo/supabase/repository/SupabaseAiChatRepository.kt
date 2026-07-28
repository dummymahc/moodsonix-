package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.models.SupabaseAiChatMessage
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the `ai_chat_history` table.
 *
 * Persists AI mood-playlist conversations so the user can review past sessions
 * and the AI model can be primed with prior context.
 *
 * Sessions are identified by an arbitrary [sessionId] string (typically a UUID
 * generated when the user starts a new chat).
 */
@Singleton
class SupabaseAiChatRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    private companion object {
        const val TABLE = "ai_chat_history"
    }

    /**
     * Returns all messages for the given [sessionId], oldest first — suitable
     * for passing directly to an LLM as conversation history.
     */
    suspend fun getSession(sessionId: String): Result<List<SupabaseAiChatMessage>> =
        runCatching {
            val uid = requireUid()
            supabase.from(TABLE)
                .select {
                    filter {
                        eq("user_id", uid)
                        eq("session_id", sessionId)
                    }
                    order("created_at", Order.ASCENDING)
                }
                .decodeList<SupabaseAiChatMessage>()
        }.onFailure { Timber.e(it, "Supabase ai_chat: getSession failed [$sessionId]") }

    /** Returns a list of distinct session IDs for the current user (most recent first). */
    suspend fun listSessions(): Result<List<String>> = runCatching {
        val uid = requireUid()
        // Fetch all rows with only the session_id column, then deduplicate client-side
        // (Supabase PostgREST supports SELECT DISTINCT but only via raw SQL; client
        //  dedup is simpler and safe for the small number of sessions expected here).
        supabase.from(TABLE)
            .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("session_id", "created_at")) {
                filter { eq("user_id", uid) }
                order("created_at", Order.DESCENDING)
            }
            .decodeList<SupabaseAiChatMessage>()
            .mapNotNull { it.sessionId }
            .distinct()
    }.onFailure { Timber.e(it, "Supabase ai_chat: listSessions failed") }

    /** Appends a single [message] to the history. */
    suspend fun saveMessage(message: SupabaseAiChatMessage): Result<Unit> = runCatching {
        supabase.from(TABLE).insert(message)
        Timber.d("Supabase ai_chat: saved [${message.role}] for session [${message.sessionId}]")
    }.onFailure { Timber.e(it, "Supabase ai_chat: saveMessage failed") }

    /** Deletes all messages belonging to [sessionId]. */
    suspend fun deleteSession(sessionId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        supabase.from(TABLE).delete {
            filter {
                eq("user_id", uid)
                eq("session_id", sessionId)
            }
        }
        Timber.d("Supabase ai_chat: deleted session [$sessionId]")
    }.onFailure { Timber.e(it, "Supabase ai_chat: deleteSession failed") }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireUid(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase: cannot access ai_chat_history — user is not signed in")
}
