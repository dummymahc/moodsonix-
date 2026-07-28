package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.models.SupabaseAppSettings
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for the `app_settings` table.
 *
 * Syncs selected user preferences (theme, audio quality, etc.) to Supabase so
 * they roam between devices.  The full DataStore blob is stored as a JSONB
 * object — only keys the caller explicitly includes are synced; none of the
 * sensitive internal state (cookies, tokens) is ever written here.
 */
@Singleton
class SupabaseSettingsRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    private companion object {
        const val TABLE = "app_settings"
    }

    /** Fetches the current user's synced settings, or null if none saved yet. */
    suspend fun getSettings(): Result<SupabaseAppSettings?> = runCatching {
        val uid = requireUid()
        supabase.from(TABLE)
            .select { filter { eq("user_id", uid) } }
            .decodeSingleOrNull<SupabaseAppSettings>()
    }.onFailure { Timber.e(it, "Supabase settings: getSettings failed") }

    /**
     * Upserts the settings for the current user.
     * [json] should contain only safe, non-sensitive preference keys.
     */
    suspend fun saveSettings(json: JsonObject): Result<Unit> = runCatching {
        val uid = requireUid()
        val payload = SupabaseAppSettings(
            userId       = uid,
            settingsJson = json,
            updatedAt    = java.time.Instant.now().toString(),
        )
        // ON CONFLICT (user_id) DO UPDATE — the table has a UNIQUE constraint on user_id
        supabase.from(TABLE).upsert(payload) {
            onConflict = "user_id"
        }
        Timber.d("Supabase settings: saved for $uid")
    }.onFailure { Timber.e(it, "Supabase settings: saveSettings failed") }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun requireUid(): String =
        supabase.auth.currentUserOrNull()?.id
            ?: error("Supabase: cannot access app_settings — user is not signed in")
}
