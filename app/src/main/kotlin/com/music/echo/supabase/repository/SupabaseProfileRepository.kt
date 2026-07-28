package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.models.SupabaseProfile
import iad1tya.echo.music.supabase.models.SupabaseProfileUpdate
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for reading and writing user profile data in the `profiles` table.
 *
 * The `profiles` table is protected by RLS — users can only update their own row,
 * though all rows are readable (public usernames / avatars).
 */
@Singleton
class SupabaseProfileRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    private companion object {
        const val TABLE = "profiles"
    }

    /** Fetches the profile for [userId]. */
    suspend fun getProfile(userId: String): Result<SupabaseProfile?> = runCatching {
        supabase.from(TABLE)
            .select {
                filter { eq("id", userId) }
            }
            .decodeSingleOrNull<SupabaseProfile>()
    }.onFailure { Timber.e(it, "Supabase profile: getProfile failed for $userId") }

    /** Fetches the currently signed-in user's profile. */
    suspend fun getCurrentProfile(): Result<SupabaseProfile?> {
        val uid = supabase.auth.currentUserOrNull()?.id
            ?: return Result.failure(IllegalStateException("Not signed in"))
        return getProfile(uid)
    }

    /**
     * Updates the currently signed-in user's profile.
     * Only the non-null fields in [update] are patched.
     */
    suspend fun updateProfile(update: SupabaseProfileUpdate): Result<Unit> = runCatching {
        val uid = supabase.auth.currentUserOrNull()?.id
            ?: error("Not signed in")

        supabase.from(TABLE)
            .update(update) {
                filter { eq("id", uid) }
            }
        Timber.d("Supabase profile: updated for $uid")
    }.onFailure { Timber.e(it, "Supabase profile: updateProfile failed") }

    /** Search profiles by username prefix (for the messaging user-search UI). */
    suspend fun searchProfiles(query: String): Result<List<SupabaseProfile>> = runCatching {
        supabase.from(TABLE)
            .select {
                filter { ilike("username", "%$query%") }
                limit(20)
            }
            .decodeList<SupabaseProfile>()
    }.onFailure { Timber.e(it, "Supabase profile: search failed for '$query'") }
}
