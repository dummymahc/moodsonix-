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
 * Supabase Email / Password sign-in screen.
 *
 * Navigation:
 * - Back arrow → pop back stack
 * - "Sign up" text button → navigates to [SupabaseRegisterScreen]
 * - Successful sign-in → pops back stack (caller decides what to show next)
 *
 * Follows the same Compose + Hilt + MVVM pattern used throughout the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupabaseLoginScreen(
    navController: NavController,
    viewModel: SupabaseAuthViewModel = hiltViewModel(),
) {
    val authState by viewModel.authState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate away when sign-in succeeds
    LaunchedEffect(authState) {
        when (authState) {
            is SupabaseAuthState.Success -> {
                viewModel.resetAuthState()
                navController.popBackStack()
            }
            is SupabaseAuthState.Error -> {
                snackbarHostState.showSnackbar(
                    message     = (authState as SupabaseAuthState.Error).message,
                    duration    = SnackbarDuration.Short,
                )
                viewModel.resetAuthState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign In") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LoginForm(
            modifier       = Modifier.padding(padding),
            isLoading      = authState is SupabaseAuthState.Loading,
            onSignIn       = { email, password -> viewModel.signIn(email, password) },
            onNavigateToRegister = {
                navController.navigate("supabase_register")
            },
        )
    }
}

// ─── Login Form ───────────────────────────────────────────────────────────────

@Composable
private fun LoginForm(
    modifier: Modifier = Modifier,
    isLoading: Boolean,
    onSignIn: (email: String, password: String) -> Unit,
    onNavigateToRegister: () -> Unit,
) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPwd  by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        // ── App branding ──────────────────────────────────────────────────────
        Text(
            text  = "Moodsonix",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text  = "Sign in to sync your music",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.padding(top = 4.dp, bottom = 32.dp),
        )

        // ── Email field ───────────────────────────────────────────────────────
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

        // ── Password field ────────────────────────────────────────────────────
        OutlinedTextField(
            value         = password,
            onValueChange = { password = it },
            label         = { Text("Password") },
            leadingIcon   = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon  = {
                IconButton(onClick = { showPwd = !showPwd }) {
                    Icon(
                        imageVector = if (showPwd) Icons.Default.VisibilityOff
                                      else         Icons.Default.Visibility,
                        contentDescription = if (showPwd) "Hide password" else "Show password",
                    )
                }
            },
            visualTransformation = if (showPwd) VisualTransformation.None
                                   else         PasswordVisualTransformation(),
            singleLine    = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction    = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (email.isNotBlank() && password.isNotBlank()) {
                        onSignIn(email.trim(), password)
                    }
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        )

        // ── Sign-in button / progress ─────────────────────────────────────────
        AnimatedVisibility(visible = isLoading, enter = fadeIn(), exit = fadeOut()) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        }

        Button(
            onClick  = { onSignIn(email.trim(), password) },
            enabled  = email.isNotBlank() && password.isNotBlank() && !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign In")
        }

        Spacer(Modifier.height(16.dp))

        // ── Navigate to Register ──────────────────────────────────────────────
        TextButton(onClick = onNavigateToRegister) {
            Text("Don't have an account? Sign up")
        }
    }
}
