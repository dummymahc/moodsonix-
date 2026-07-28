package iad1tya.echo.music.supabase

import iad1tya.echo.music.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import timber.log.Timber

/**
 * Creates and exposes the single [SupabaseClient] instance used across the app.
 *
 * The URL and anon key are read from BuildConfig, which in turn reads them from
 * local.properties (or CI environment variables) — they are never hard-coded.
 *
 * Installed plugins:
 * - [Auth]      → Email/Password sign-up, sign-in, sign-out, session persistence
 * - [Postgrest] → Type-safe access to all Supabase PostgreSQL tables
 * - [Realtime]  → Live Postgres change streams + Presence for typing indicators
 */
object SupabaseClientProvider {

    /** True only when both URL and anon key have been configured. */
    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() &&
                BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    /**
     * Lazily created [SupabaseClient]. Returns null and logs a warning if the
     * project has not been configured (e.g. an F-Droid build that ships without
     * credentials — all Supabase features gracefully degrade to no-ops).
     */
    val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            Timber.w("Supabase: SUPABASE_URL or SUPABASE_ANON_KEY is not set. " +
                     "Add them to local.properties — see supabase.properties.template.")
            return@lazy null
        }

        try {
            createSupabaseClient(
                supabaseUrl  = BuildConfig.SUPABASE_URL,
                supabaseKey  = BuildConfig.SUPABASE_ANON_KEY,
            ) {
                // Auth plugin — handles sign-up/in/out and session storage
                install(Auth)

                // Postgrest plugin — used by all repository classes for CRUD
                install(Postgrest)

                // Realtime plugin — powers live messaging and typing indicators
                install(Realtime)
            }.also {
                Timber.d("Supabase: client initialised for ${BuildConfig.SUPABASE_URL}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Supabase: failed to create client")
            null
        }
    }
}
