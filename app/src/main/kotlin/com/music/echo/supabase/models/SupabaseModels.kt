package iad1tya.echo.music.supabase.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Moodsonix · Supabase Data Models
//
// Every class is annotated with @Serializable so the Supabase Kotlin SDK can
// automatically encode/decode them from JSON.  Field names use @SerialName to
// match the snake_case column names in Postgres exactly.
// =============================================================================

// ─── Profiles ─────────────────────────────────────────────────────────────────

/**
 * Mirrors the `profiles` table.  One row per auth.users record.
 */
@Serializable
data class SupabaseProfile(
    @SerialName("id")           val id: String,
    @SerialName("username")     val username: String?      = null,
    @SerialName("display_name") val displayName: String?   = null,
    @SerialName("avatar_url")   val avatarUrl: String?     = null,
    @SerialName("bio")          val bio: String?           = null,
    @SerialName("created_at")   val createdAt: String?     = null,
    @SerialName("updated_at")   val updatedAt: String?     = null,
)

/**
 * Payload sent when updating a profile row (only non-null fields are patched).
 */
@Serializable
data class SupabaseProfileUpdate(
    @SerialName("username")     val username: String?     = null,
    @SerialName("display_name") val displayName: String?  = null,
    @SerialName("avatar_url")   val avatarUrl: String?    = null,
    @SerialName("bio")          val bio: String?          = null,
    // Use java.time.Instant (available via desugaring on API 26+) for the default timestamp
    @SerialName("updated_at")   val updatedAt: String     = java.time.Instant.now().toString(),
)

// ─── Favorites ────────────────────────────────────────────────────────────────

/**
 * Mirrors the `favorites` table.
 */
@Serializable
data class SupabaseFavorite(
    @SerialName("id")             val id: String?     = null,
    @SerialName("user_id")        val userId: String? = null,
    @SerialName("song_id")        val songId: String,
    @SerialName("song_title")     val songTitle: String,
    @SerialName("song_artist")    val songArtist: String?   = null,
    @SerialName("song_thumbnail") val songThumbnail: String? = null,
    @SerialName("song_duration")  val songDuration: Int?    = null,
    @SerialName("created_at")     val createdAt: String?    = null,
)

// ─── Playlists ────────────────────────────────────────────────────────────────

@Serializable
data class SupabasePlaylist(
    @SerialName("id")            val id: String?     = null,
    @SerialName("user_id")       val userId: String? = null,
    @SerialName("name")          val name: String,
    @SerialName("description")   val description: String?   = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String?  = null,
    @SerialName("is_public")     val isPublic: Boolean      = false,
    @SerialName("created_at")    val createdAt: String?     = null,
    @SerialName("updated_at")    val updatedAt: String?     = null,
)

@Serializable
data class SupabasePlaylistSong(
    @SerialName("id")             val id: String?     = null,
    @SerialName("playlist_id")    val playlistId: String,
    @SerialName("user_id")        val userId: String? = null,
    @SerialName("song_id")        val songId: String,
    @SerialName("song_title")     val songTitle: String,
    @SerialName("song_artist")    val songArtist: String?    = null,
    @SerialName("song_thumbnail") val songThumbnail: String? = null,
    @SerialName("song_duration")  val songDuration: Int?     = null,
    @SerialName("position")       val position: Int          = 0,
    @SerialName("added_at")       val addedAt: String?       = null,
)

// ─── Recently Played ──────────────────────────────────────────────────────────

@Serializable
data class SupabaseRecentlyPlayed(
    @SerialName("id")             val id: String?     = null,
    @SerialName("user_id")        val userId: String? = null,
    @SerialName("song_id")        val songId: String,
    @SerialName("song_title")     val songTitle: String,
    @SerialName("song_artist")    val songArtist: String?    = null,
    @SerialName("song_thumbnail") val songThumbnail: String? = null,
    @SerialName("song_duration")  val songDuration: Int?     = null,
    @SerialName("played_at")      val playedAt: String?      = null,
)

// ─── AI Chat History ──────────────────────────────────────────────────────────

@Serializable
data class SupabaseAiChatMessage(
    @SerialName("id")         val id: String?     = null,
    @SerialName("user_id")    val userId: String? = null,
    @SerialName("session_id") val sessionId: String? = null,
    @SerialName("role")       val role: String,     // "user" | "assistant" | "system"
    @SerialName("content")    val content: String,
    @SerialName("created_at") val createdAt: String? = null,
)

// ─── App Settings ─────────────────────────────────────────────────────────────

@Serializable
data class SupabaseAppSettings(
    @SerialName("id")            val id: String?     = null,
    @SerialName("user_id")       val userId: String? = null,
    /** Arbitrary JSON blob — the app serializes its DataStore preferences here. */
    @SerialName("settings_json") val settingsJson: kotlinx.serialization.json.JsonObject =
        kotlinx.serialization.json.JsonObject(emptyMap()),
    @SerialName("updated_at")    val updatedAt: String? = null,
)

// ─── Messaging ────────────────────────────────────────────────────────────────

@Serializable
data class SupabaseMessage(
    @SerialName("id")          val id: String?     = null,
    @SerialName("sender_id")   val senderId: String,
    @SerialName("receiver_id") val receiverId: String,
    @SerialName("content")     val content: String,
    @SerialName("is_read")     val isRead: Boolean = false,
    @SerialName("created_at")  val createdAt: String? = null,
    @SerialName("updated_at")  val updatedAt: String? = null,
)

@Serializable
data class SupabaseConversation(
    @SerialName("id")               val id: String?     = null,
    @SerialName("participant1_id")  val participant1Id: String,
    @SerialName("participant2_id")  val participant2Id: String,
    @SerialName("last_message_at")  val lastMessageAt: String? = null,
    @SerialName("created_at")       val createdAt: String?     = null,
)

// ─── Typing Indicator (Presence payload) ──────────────────────────────────────

/**
 * Sent via Supabase Presence channels to show who is typing.
 */
@Serializable
data class TypingPresence(
    @SerialName("user_id")      val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_typing")    val isTyping: Boolean,
)
