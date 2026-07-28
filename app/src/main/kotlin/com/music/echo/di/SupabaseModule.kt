package iad1tya.echo.music.di

import iad1tya.echo.music.supabase.SupabaseClientProvider
import iad1tya.echo.music.supabase.repository.SupabaseAiChatRepository
import iad1tya.echo.music.supabase.repository.SupabaseAuthRepository
import iad1tya.echo.music.supabase.repository.SupabaseFavoritesRepository
import iad1tya.echo.music.supabase.repository.SupabaseMessagingRepository
import iad1tya.echo.music.supabase.repository.SupabasePlaylistRepository
import iad1tya.echo.music.supabase.repository.SupabaseProfileRepository
import iad1tya.echo.music.supabase.repository.SupabaseRecentlyPlayedRepository
import iad1tya.echo.music.supabase.repository.SupabaseSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/**
 * Hilt DI Module for all Supabase-related dependencies.
 *
 * The [SupabaseClient] is provided as a singleton and injected into every
 * repository.  If the app has not been configured with SUPABASE_URL /
 * SUPABASE_ANON_KEY, [SupabaseClientProvider.client] returns null and Hilt
 * will throw at startup — so remember to fill in local.properties before
 * building (see supabase.properties.template).
 *
 * All repositories follow the same MVVM pattern used elsewhere in the project:
 * they are @Singleton-scoped services injected into ViewModels.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    // ── Core client ───────────────────────────────────────────────────────────

    /**
     * Provides the single [SupabaseClient] instance.
     *
     * Throws [IllegalStateException] at first injection if SUPABASE_URL or
     * SUPABASE_ANON_KEY is blank.  This surfaces the misconfiguration early
     * (at app startup) rather than silently at the first network call.
     */
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        SupabaseClientProvider.client
            ?: error(
                "Supabase client is not configured. " +
                "Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties. " +
                "See supabase.properties.template at the project root."
            )

    // ── Repositories ──────────────────────────────────────────────────────────

    @Provides @Singleton
    fun provideAuthRepository(client: SupabaseClient) =
        SupabaseAuthRepository(client)

    @Provides @Singleton
    fun provideProfileRepository(client: SupabaseClient) =
        SupabaseProfileRepository(client)

    @Provides @Singleton
    fun provideFavoritesRepository(client: SupabaseClient) =
        SupabaseFavoritesRepository(client)

    @Provides @Singleton
    fun providePlaylistRepository(client: SupabaseClient) =
        SupabasePlaylistRepository(client)

    @Provides @Singleton
    fun provideRecentlyPlayedRepository(client: SupabaseClient) =
        SupabaseRecentlyPlayedRepository(client)

    @Provides @Singleton
    fun provideAiChatRepository(client: SupabaseClient) =
        SupabaseAiChatRepository(client)

    @Provides @Singleton
    fun provideSettingsRepository(client: SupabaseClient) =
        SupabaseSettingsRepository(client)

    @Provides @Singleton
    fun provideMessagingRepository(client: SupabaseClient) =
        SupabaseMessagingRepository(client)
}
