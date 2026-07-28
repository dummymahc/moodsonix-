package iad1tya.echo.music.supabase.repository

import iad1tya.echo.music.supabase.SupabaseClientProvider
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserInfo
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles all Supabase Authentication operations.
 *
 * Responsibilities:
 * - Email / Password sign-up and sign-in
 * - Sign-out
 * - Providing the currently authenticated user / UID
 *
 * Every public function is a suspend function that returns a [Result], so the
 * calling ViewModel can surface success or failure to the UI without catching
 * exceptions itself.
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    // ──────────────────────────────────────────────────────────────────────────
    // Authentication
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Creates a new user account with [email] and [password].
     * The Supabase [Auth] plugin persists the session automatically.
     */
    suspend fun signUp(email: String, password: String): Result<Unit> = runCatching {
        supabase.auth.signUpWith(Email) {
            this.email    = email
            this.password = password
        }
        Timber.d("Supabase auth: signed up [$email]")
    }.onFailure { Timber.e(it, "Supabase auth: signUp failed") }

    /**
     * Signs in an existing user.  On success the session is stored internally
     * by the [Auth] plugin and is available via [currentUser].
     */
    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        supabase.auth.signInWith(Email) {
            this.email    = email
            this.password = password
        }
        Timber.d("Supabase auth: signed in [$email]")
    }.onFailure { Timber.e(it, "Supabase auth: signIn failed") }

    /**
     * Signs the user out and clears the local session.
     */
    suspend fun signOut(): Result<Unit> = runCatching {
        supabase.auth.signOut()
        Timber.d("Supabase auth: signed out")
    }.onFailure { Timber.e(it, "Supabase auth: signOut failed") }

    // ──────────────────────────────────────────────────────────────────────────
    // Session helpers
    // ──────────────────────────────────────────────────────────────────────────

    /** Returns the currently authenticated [UserInfo], or null if not signed in. */
    fun currentUser(): UserInfo? = supabase.auth.currentUserOrNull()

    /** Returns the UUID string of the current user, or null if not signed in. */
    fun currentUserId(): String? = currentUser()?.id

    /** Returns true when a valid session exists. */
    fun isSignedIn(): Boolean = supabase.auth.currentSessionOrNull() != null
}
