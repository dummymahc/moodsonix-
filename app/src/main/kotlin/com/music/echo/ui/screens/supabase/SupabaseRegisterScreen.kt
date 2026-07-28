package iad1tya.echo.music.ui.screens.supabase

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import iad1tya.echo.music.viewmodels.SupabaseAuthState
import iad1tya.echo.music.viewmodels.SupabaseAuthViewModel

/**
 * Supabase Email / Password registration screen.
 *
 * On successful sign-up, the Auth plugin creates a session automatically
 * (no email-confirmation step unless enabled in the Supabase dashboard),
 * and the trigger in SQL creates the `profiles` row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseRegisterScreen(
    navController: NavController,
    viewModel: SupabaseAuthViewModel = hiltViewModel(),
) {
    val authState     by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate away after successful registration
    LaunchedEffect(authState) {
        when (authState) {
            is SupabaseAuthState.Success -> {
                viewModel.resetAuthState()
                // Go all the way back so the user lands on the main screen, not the login page
                navController.popBackStack("supabase_login", inclusive = true)
            }
            is SupabaseAuthState.Error -> {
                snackbarHostState.showSnackbar(
                    message  = (authState as SupabaseAuthState.Error).message,
                    duration = SnackbarDuration.Short,
                )
                viewModel.resetAuthState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Account") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        RegisterForm(
            modifier  = Modifier.padding(padding),
            isLoading = authState is SupabaseAuthState.Loading,
            onSignUp  = { email, password -> viewModel.signUp(email, password) },
            onNavigateToLogin = { navController.popBackStack() },
        )
    }
}

// ─── Register Form ────────────────────────────────────────────────────────────

@Composable
private fun RegisterForm(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    onSignUp: (email: String, password: String) -> Unit,
    onNavigateToLogin: () -> Unit,
) {
    var email           by remember { mutableStateOf("") }
    var password        by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPwd         by remember { mutableStateOf(false) }
    var passwordError   by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = "Join Moodsonix",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text      = "Create an account to sync favourites, playlists & chat",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )

        // ── Email ─────────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = email,
            onValueChange = { email = it },
            label         = { Text("Email") },
            leadingIcon   = { Icon(Icons.Default.Email, contentDescription = null) },
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction    = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )

        // ── Password ──────────────────────────────────────────────────────────
        OutlinedTextField(
            value         = password,
            onValueChange = {
                password      = it
                passwordError = null
            },
            label         = { Text("Password") },
            leadingIcon   = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon  = {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(
                        imageVector        = if (showPwd) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPwd) "Hide password" else "Show password",
                    )
                }
            },
            visualTransformation = if (showPwd) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
        )

        // ── Confirm Password ──────────────────────────────────────────────────
        OutlinedTextField(
            value         = confirmPassword,
            onValueChange = {
                confirmPassword = it
                passwordError   = null
            },
            label         = { Text("Confirm Password") },
            leadingIcon   = { Icon(Icons.Default.Lock, contentDescription = null) },
            isError       = passwordError != null,
            supportingText = passwordError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            visualTransformation = PasswordVisualTransformation(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    attemptSignUp(email, password, confirmPassword,
                                  onError = { passwordError = it }, onSignUp = onSignUp)
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )

        // ── Sign-up button ────────────────────────────────────────────────────
        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick  = {
                attemptSignUp(email, password, confirmPassword,
                              onError = { passwordError = it }, onSignUp = onSignUp)
            },
            enabled  = email.isNotBlank() && password.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create Account")
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onNavigateToLogin) {
            Text("Already have an account? Sign in")
        }
    }
}

// ─── Validation helper ────────────────────────────────────────────────────────

private fun attemptSignUp(
    email: String,
    password: String,
    confirm: String,
    onError: (String) -> Unit,
    onSignUp: (String, String) -> Unit,
) {
    when {
        email.isBlank()          -> onError("Email cannot be empty")
        password.length < 6      -> onError("Password must be at least 6 characters")
        password != confirm      -> onError("Passwords do not match")
        else                     -> onSignUp(email.trim(), password)
    }
}
