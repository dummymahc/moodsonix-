package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.models.SupabaseFavorite
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the `favorites` table.
 *
 * Syncs the local Room "liked songs" concept to Supabase so the user's
 * favourite tracks are available across devices.
 *
 * RLS ensures every query is automatically scoped to the current user —
 * the `user_id` filter is still added explicitly for clarity.
 */
@Singleton
class SupabaseFavoritesRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    private companion object {
        const val TABLE = "favorites"
    }

    /** Returns all favorites for the current user, newest first. */
    suspend fun getFavorites(): Result<List<SupabaseFavorite>> = runCatching {
        val uid = requireUid()
        supabase.from(TABLE)
            .select {
                filter { eq("user_id", uid) }
                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }
            .decodeList<SupabaseFavorite>()
    }.onFailure { Timber.e(it, "Supabase favorites: getFavorites failed") }

    /** Adds a song to the current user's favorites. */
    suspend fun addFavorite(favorite: SupabaseFavorite): Result<Unit> = runCatching {
        supabase.from(TABLE).insert(favorite)
        Timber.d("Supabase favorites: added [${favorite.songId}]")
    }.onFailure { Timber.e(it, "Supabase favorites: addFavorite failed [${favorite.songId}]") }

    /** Removes a song from favorites by its [songId]. */
    suspend fun removeFavorite(songId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        supabase.from(TABLE)
            .delete {
                filter {
                    eq("user_id", uid)
                    eq("song_id", songId)
                }
            }
        Timber.d("Supabase favorites: removed [$songId]")
    }.onFailure { Timber.e(it, "Supabase favorites: removeFavorite failed [$songId]") }

    /** Returns true if the given song is in the current user's favorites. */
    suspend fun isFavorite(songId: String): Result<Boolean> = runCatching {
        val uid = requireUid()
        val rows = supabase.from(TABLE)
            .select {
                filter {
                    eq("user_id", uid)
                    eq("song_id", songId)
                }
                limit(1)
            }
            .decodeList<SupabaseFavorite>()
        rows.isNotEmpty()
    }.onFailure { Timber.e(it, "Supabase favorites: isFavorite failed [$songId]") }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireUid(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase: cannot access favorites — user is not signed in")
}
