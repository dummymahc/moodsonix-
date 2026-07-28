package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.models.SupabasePlaylist
import iad1tya.echo.music.supabase.models.SupabasePlaylistSong
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the `playlists` and `playlist_songs` tables.
 *
 * This acts as a cloud sync layer on top of the existing local Room playlists —
 * it does NOT replace Room; it mirrors the data so users can access their
 * playlists from multiple devices.
 */
@Singleton
class SupabasePlaylistRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    // ── Playlists ──────────────────────────────────────────────────────────────

    /** Returns all playlists owned by the current user. */
    suspend fun getMyPlaylists(): Result<List<SupabasePlaylist>> = runCatching {
        val uid = requireUid()
        supabase.from("playlists")
            .select { filter { eq("user_id", uid) } }
            .decodeList<SupabasePlaylist>()
    }.onFailure { Timber.e(it, "Supabase playlists: getMyPlaylists failed") }

    /** Creates a new playlist in Supabase and returns the created row. */
    suspend fun createPlaylist(playlist: SupabasePlaylist): Result<SupabasePlaylist> =
        runCatching {
            supabase.from("playlists")
                .insert(playlist) { select() }
                .decodeSingle<SupabasePlaylist>()
                .also { Timber.d("Supabase playlists: created [${it.id}] '${it.name}'") }
        }.onFailure { Timber.e(it, "Supabase playlists: createPlaylist failed") }

    /** Updates the metadata of an existing playlist. */
    suspend fun updatePlaylist(playlistId: String, name: String, description: String?,
                               isPublic: Boolean): Result<Unit> = runCatching {
        val uid = requireUid()
        supabase.from("playlists").update(
            mapOf("name" to name, "description" to description, "is_public" to isPublic,
                  "updated_at" to java.time.Instant.now().toString())
        ) {
            filter {
                eq("id", playlistId)
                eq("user_id", uid)   // RLS also enforces this, but be explicit
            }
        }
        Timber.d("Supabase playlists: updated [$playlistId]")
    }.onFailure { Timber.e(it, "Supabase playlists: updatePlaylist failed") }

    /** Deletes a playlist and all its songs (cascade handled by FK in Postgres). */
    suspend fun deletePlaylist(playlistId: String): Result<Unit> = runCatching {
        val uid = requireUid()
        supabase.from("playlists").delete {
            filter {
                eq("id", playlistId)
                eq("user_id", uid)
            }
        }
        Timber.d("Supabase playlists: deleted [$playlistId]")
    }.onFailure { Timber.e(it, "Supabase playlists: deletePlaylist failed") }

    // ── Playlist Songs ─────────────────────────────────────────────────────────

    /** Returns all songs in [playlistId], ordered by position. */
    suspend fun getSongsInPlaylist(playlistId: String): Result<List<SupabasePlaylistSong>> =
        runCatching {
            supabase.from("playlist_songs")
                .select {
                    filter { eq("playlist_id", playlistId) }
                    order("position", Order.ASCENDING)
                }
                .decodeList<SupabasePlaylistSong>()
        }.onFailure { Timber.e(it, "Supabase playlists: getSongs failed for [$playlistId]") }

    /** Adds a song to the given playlist. */
    suspend fun addSongToPlaylist(song: SupabasePlaylistSong): Result<Unit> = runCatching {
        supabase.from("playlist_songs").insert(song)
        Timber.d("Supabase playlists: added song [${song.songId}] to [${song.playlistId}]")
    }.onFailure { Timber.e(it, "Supabase playlists: addSong failed") }

    /** Removes a specific song from a playlist. */
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String): Result<Unit> =
        runCatching {
            val uid = requireUid()
            supabase.from("playlist_songs").delete {
                filter {
                    eq("playlist_id", playlistId)
                    eq("song_id", songId)
                    eq("user_id", uid)
                }
            }
            Timber.d("Supabase playlists: removed song [$songId] from [$playlistId]")
        }.onFailure { Timber.e(it, "Supabase playlists: removeSong failed") }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireUid(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase: cannot access playlists — user is not signed in")
}
