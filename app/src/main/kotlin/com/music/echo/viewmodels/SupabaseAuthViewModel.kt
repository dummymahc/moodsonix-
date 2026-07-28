package iad1tya.echo.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import iad1tya.echo.music.supabase.models.SupabaseProfile
import iad1tya.echo.music.supabase.models.SupabaseProfileUpdate
import iad1tya.echo.music.supabase.repository.SupabaseAuthRepository
import iad1tya.echo.music.supabase.repository.SupabaseProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

// ─── UI State ─────────────────────────────────────────────────────────────────

/** Represents the possible states of any auth/profile network operation. */
sealed class SupabaseAuthState {
    object Idle        : SupabaseAuthState()
    object Loading     : SupabaseAuthState()
    object Success     : SupabaseAuthState()
    data class Error(val message: String) : SupabaseAuthState()
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

/**
 * ViewModel for Supabase Authentication and User Profile management.
 *
 * Exposes [StateFlow]s that the UI observes:
 * - [authState]    → loading / success / error feedback for auth operations
 * - [currentUser]  → the currently signed-in [UserInfo] (null when logged out)
 * - [profile]      → the signed-in user's [SupabaseProfile]
 * - [isSignedIn]   → convenience boolean derived from [currentUser]
 *
 * All mutations happen in [viewModelScope] so they are automatically cancelled
 * when the ViewModel is cleared.
 */
@HiltViewModel
class SupabaseAuthViewModel @Inject constructor(
    private val authRepository: SupabaseAuthRepository,
    private val profileRepository: SupabaseProfileRepository,
) : ViewModel() {

    // ── Exposed state ──────────────────────────────────────────────────────────

    private val _authState  = MutableStateFlow<SupabaseAuthState>(SupabaseAuthState.Idle)
    val authState: StateFlow<SupabaseAuthState> = _authState.asStateFlow()

    private val _currentUser = MutableStateFlow<UserInfo?>(null)
    val currentUser: StateFlow<UserInfo?> = _currentUser.asStateFlow()

    private val _profile = MutableStateFlow<SupabaseProfile?>(null)
    val profile: StateFlow<SupabaseProfile?> = _profile.asStateFlow()

    val isSignedIn: Boolean get() = authRepository.isSignedIn()

    init {
        // Restore session if one was persisted from a previous launch
        refreshSession()
    }

    // ── Auth operations ────────────────────────────────────────────────────────

    /**
     * Creates a new Supabase account and loads the resulting profile.
     * Emits [SupabaseAuthState.Success] on the happy path or
     * [SupabaseAuthState.Error] with the exception message otherwise.
     */
    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = SupabaseAuthState.Loading
            authRepository.signUp(email, password)
                .onSuccess {
                    _currentUser.value = authRepository.currentUser()
                    loadProfile()
                    _authState.value = SupabaseAuthState.Success
                    Timber.d("SupabaseAuthViewModel: signUp success")
                }
                .onFailure { e ->
                    _authState.value = SupabaseAuthState.Error(
                        e.message ?: "Sign up failed. Check your email and try again."
                    )
                    Timber.e(e, "SupabaseAuthViewModel: signUp failed")
                }
        }
    }

    /**
     * Signs in with [email] and [password].
     */
    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = SupabaseAuthState.Loading
            authRepository.signIn(email, password)
                .onSuccess {
                    _currentUser.value = authRepository.currentUser()
                    loadProfile()
                    _authState.value = SupabaseAuthState.Success
                    Timber.d("SupabaseAuthViewModel: signIn success")
                }
                .onFailure { e ->
                    _authState.value = SupabaseAuthState.Error(
                        e.message ?: "Sign in failed. Check your credentials."
                    )
                    Timber.e(e, "SupabaseAuthViewModel: signIn failed")
                }
        }
    }

    /**
     * Signs out and clears all local state.
     */
    fun signOut() {
        viewModelScope.launch {
            _authState.value = SupabaseAuthState.Loading
            authRepository.signOut()
                .onSuccess {
                    _currentUser.value = null
                    _profile.value     = null
                    _authState.value   = SupabaseAuthState.Success
                    Timber.d("SupabaseAuthViewModel: signOut success")
                }
                .onFailure { e ->
                    _authState.value = SupabaseAuthState.Error(
                        e.message ?: "Sign out failed."
                    )
                }
        }
    }

    // ── Profile operations ─────────────────────────────────────────────────────

    /**
     * Updates the current user's display name, avatar URL, and/or bio.
     */
    fun updateProfile(displayName: String? = null,
                      avatarUrl: String?   = null,
                      bio: String?         = null,
                      username: String?    = null) {
        viewModelScope.launch {
            _authState.value = SupabaseAuthState.Loading
            val update = SupabaseProfileUpdate(
                username    = username,
                displayName = displayName,
                avatarUrl   = avatarUrl,
                bio         = bio,
            )
            profileRepository.updateProfile(update)
                .onSuccess {
                    loadProfile()          // refresh local copy
                    _authState.value = SupabaseAuthState.Success
                }
                .onFailure { e ->
                    _authState.value = SupabaseAuthState.Error(
                        e.message ?: "Profile update failed."
                    )
                }
        }
    }

    /** Manually resets [authState] to [SupabaseAuthState.Idle] (e.g. after showing a Snackbar). */
    fun resetAuthState() {
        _authState.value = SupabaseAuthState.Idle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun refreshSession() {
        _currentUser.value = authRepository.currentUser()
        if (_currentUser.value != null) {
            viewModelScope.launch { loadProfile() }
        }
    }

    private suspend fun loadProfile() {
        profileRepository.getCurrentProfile()
            .onSuccess { _profile.value = it }
            .onFailure { Timber.e(it, "SupabaseAuthViewModel: loadProfile failed") }
    }
}
