package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.models.SupabaseRecentlyPlayed
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the `recently_played` table.
 *
 * Called each time the user starts a track; keeps a cloud-synced history so
 * the "Recently Played" list is consistent across devices.
 */
@Singleton
class SupabaseRecentlyPlayedRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    private companion object {
        const val TABLE = "recently_played"
        /** Maximum rows retained per user.  Older rows are pruned on each insert. */
        const val MAX_HISTORY = 200
    }

    /** Returns the user's recently played list, newest first. */
    suspend fun getRecentlyPlayed(limit: Int = 50): Result<List<SupabaseRecentlyPlayed>> =
        runCatching {
            val uid = requireUid()
            supabase.from(TABLE)
                .select {
                    filter { eq("user_id", uid) }
                    order("played_at", Order.DESCENDING)
                    limit(limit.toLong())
                }
                .decodeList<SupabaseRecentlyPlayed>()
        }.onFailure { Timber.e(it, "Supabase recently_played: getRecentlyPlayed failed") }

    /**
     * Records that the user played [song].
     * Automatically trims history to [MAX_HISTORY] rows to keep storage lean.
     */
    suspend fun recordPlay(song: SupabaseRecentlyPlayed): Result<Unit> = runCatching {
        supabase.from(TABLE).insert(song)
        Timber.d("Supabase recently_played: recorded [${song.songId}]")
        trimHistory()
    }.onFailure { Timber.e(it, "Supabase recently_played: recordPlay failed") }

    /** Clears the entire recently-played history for the current user. */
    suspend fun clearHistory(): Result<Unit> = runCatching {
        val uid = requireUid()
        supabase.from(TABLE).delete { filter { eq("user_id", uid) } }
        Timber.d("Supabase recently_played: history cleared")
    }.onFailure { Timber.e(it, "Supabase recently_played: clearHistory failed") }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Deletes rows beyond [MAX_HISTORY] to avoid unbounded table growth.
     * Runs as a fire-and-forget; errors are logged but not surfaced.
     */
    private suspend fun trimHistory() {
        try {
            val uid = requireUid()
            // Fetch IDs of ALL rows sorted oldest-first; delete everything past the limit
            val allRows = supabase.from(TABLE)
                .select {
                    filter { eq("user_id", uid) }
                    order("played_at", Order.DESCENDING)
                }
                .decodeList<SupabaseRecentlyPlayed>()

            if (allRows.size > MAX_HISTORY) {
                val idsToDelete = allRows.drop(MAX_HISTORY).mapNotNull { it.id }
                if (idsToDelete.isNotEmpty()) {
                    supabase.from(TABLE).delete {
                        filter { isIn("id", idsToDelete) }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Supabase recently_played: trimHistory failed (non-critical)")
        }
    }

    private fun requireUid(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase: cannot access recently_played — user is not signed in")
}
