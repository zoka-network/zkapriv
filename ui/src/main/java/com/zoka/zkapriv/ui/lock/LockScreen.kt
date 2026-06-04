package com.zoka.zkapriv.ui.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Unlock screen. Fintech-style layout: a decorative top gradient (black in
 * light theme, mirrored to a light band in dark theme), the ZOKA NETWORK
 * wordmark, a personal greeting using the active account label, a
 * security/session notice, then a primary biometric button with a PIN
 * fallback. "Forgot your PIN?" opens the existing wallet-reset flow.
 */
@Composable
fun LockScreen(
    onUnlocked: () -> Unit,
    onWalletReset: () -> Unit = {},
    viewModel: LockViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current as FragmentActivity
    var pin by remember { mutableStateOf("") }
    var showPinInput by remember { mutableStateOf(false) }

    // Theme detection based on background luminance
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDarkTheme = remember(backgroundColor) {
        (backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f) < 0.5f
    }

    val buttonContainerColor = if (isDarkTheme) Color.White else Color.Black
    val buttonContentColor = if (isDarkTheme) Color.Black else Color.White
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val accent = MaterialTheme.colorScheme.primary

    // Decorative top gradient. Light theme: black at the top fading down into
    // the light background. Dark theme: mirrored — a light band fading down
    // into the dark background.
    val gradientBrush = Brush.verticalGradient(
        colors = if (isDarkTheme)
            listOf(Color.White.copy(alpha = 0.16f), backgroundColor.copy(alpha = 0f))
        else
            listOf(Color.Black.copy(alpha = 0.90f), backgroundColor.copy(alpha = 0f))
    )

    val biometricPrompt = remember {
        BiometricPrompt(
            context,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onBiometricSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.onBiometricError(errString.toString())
                }
                override fun onAuthenticationFailed() {
                    viewModel.onBiometricError("Authentication failed")
                }
            }
        )
    }

    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock zkapriv")
            .setSubtitle("Use your biometric credential")
            .setNegativeButtonText("Use PIN")
            .build()
    }

    LaunchedEffect(uiState.showBiometricPrompt) {
        if (uiState.showBiometricPrompt) {
            biometricPrompt.authenticate(promptInfo)
            viewModel.resetBiometricPrompt()
        }
    }
    LaunchedEffect(uiState.isUnlocked) { if (uiState.isUnlocked) onUnlocked() }
    LaunchedEffect(uiState.walletReset) { if (uiState.walletReset) onWalletReset() }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val logoScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.6f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "logoScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(500),
        label = "contentAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        // Full-bleed decorative gradient band at the top (behind the status bar).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.TopCenter)
                .background(gradientBrush)
        )

        // Version label, top-right.
        Text(
            text = "v0.1.0",
            fontSize = 12.sp,
            color = if (isDarkTheme) textSecondary else Color.White.copy(alpha = 0.7f),
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 24.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = contentAlpha },
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(if (showPinInput) 24.dp else 64.dp))

            // ZOKA NETWORK wordmark. White-tinted on dark theme; on light theme
            // it sits over the dark gradient band, so tint it white there too.
            Image(
                painter = painterResource(id = com.zoka.zkapriv.ui.R.drawable.logoportada),
                contentDescription = "Zoka Network",
                modifier = Modifier
                    .height(if (showPinInput) 40.dp else 60.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    },
                colorFilter = ColorFilter.tint(Color.White)
            )

            Spacer(modifier = Modifier.height(if (showPinInput) 24.dp else 72.dp))

            if (!showPinInput) {
                // Greeting + reset link
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.walletName.isNotBlank())
                            "Hello, ${uiState.walletName}!"
                        else "Welcome back",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = { viewModel.showForgotPasswordDialog(true) },
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Forgot your PIN?",
                            color = accent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Push the notice + sign-in actions to the bottom of the screen.
                Spacer(modifier = Modifier.weight(1f))

                // Session / security notice
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Security,
                            contentDescription = null,
                            tint = textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Your session expired for security. Sign in again to continue.",
                            fontSize = 14.sp,
                            color = textPrimary.copy(alpha = 0.85f)
                        )
                    }
                }

                if (uiState.error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.error!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                if (uiState.biometricAvailable) {
                    // Primary: biometric
                    Button(
                        onClick = { viewModel.authenticateWithBiometric() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (uiState.isLoading && !uiState.showBiometricPrompt) {
                            CircularProgressIndicator(
                                color = buttonContentColor,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Sign in with biometrics",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = {
                            showPinInput = true
                            viewModel.clearError()
                        }) {
                            Text(
                                "Use PIN",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                        }
                    }
                } else {
                    // No biometric enrolled: PIN is the primary action.
                    Button(
                        onClick = {
                            showPinInput = true
                            viewModel.clearError()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Sign in with PIN", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // ── PIN entry state ───────────────────────────────────────────
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isDarkTheme) 0.dp else 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = CircleShape,
                            color = if (uiState.error != null)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                            else buttonContainerColor
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = if (uiState.error != null)
                                        MaterialTheme.colorScheme.error
                                    else buttonContentColor
                                )
                            }
                        }

                        Text(
                            text = if (uiState.error != null) uiState.error!! else "Enter your PIN",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (uiState.error != null) MaterialTheme.colorScheme.error else textPrimary
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            repeat(6) { index ->
                                PinDot(
                                    isFilled = index < pin.length,
                                    isError = uiState.error != null,
                                    isDarkTheme = isDarkTheme
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                NumberPad(
                    onNumberClick = { number ->
                        if (pin.length < 6) {
                            if (pin.isEmpty()) viewModel.clearError()
                            pin += number
                            if (pin.length == 6) {
                                viewModel.verifyPin(pin)
                                if (uiState.error != null) pin = ""
                            }
                        }
                    },
                    onDeleteClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    isDarkTheme = isDarkTheme,
                    buttonContainerColor = buttonContainerColor,
                    buttonContentColor = buttonContentColor
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.biometricAvailable) {
                            TextButton(onClick = {
                                showPinInput = false
                                viewModel.clearError()
                                pin = ""
                            }) {
                                Text("Use biometrics", fontSize = 14.sp, color = textSecondary)
                            }
                        }
                        TextButton(onClick = { viewModel.showForgotPasswordDialog(true) }) {
                            Text("Forgot your PIN?", fontSize = 14.sp, color = accent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Reset wallet dialog
        if (uiState.showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!uiState.isLoading) viewModel.showForgotPasswordDialog(false)
                },
                icon = {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                },
                title = {
                    Text(text = "Reset wallet?", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                },
                text = {
                    Column {
                        Text(
                            text = "This removes wallet data from this device.",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Funds stay on chain, but you cannot recover them without your recovery phrase or scan key.",
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("- Wallet accounts stored on this phone", fontSize = 14.sp)
                        Text("- PIN and biometric unlock settings", fontSize = 14.sp)
                        Text("- Cached balances and local transaction history", fontSize = 14.sp)
                        Text("- Custom RPC and app settings", fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Continue only if your recovery phrase or scan key is backed up.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.resetWallet() },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onError,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Resetting")
                        } else {
                            Text("Reset wallet")
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.showForgotPasswordDialog(false) },
                        enabled = !uiState.isLoading
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun PinDot(isFilled: Boolean, isError: Boolean = false, isDarkTheme: Boolean = false) {
    val dotColor = when {
        isError -> MaterialTheme.colorScheme.error
        else -> if (isDarkTheme) Color.White else Color.Black
    }

    val scale by animateFloatAsState(
        targetValue = if (isFilled) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "dotScale"
    )

    Box(
        modifier = Modifier
            .size(18.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(
                color = if (isFilled) dotColor else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = 2.dp,
                color = dotColor.copy(alpha = if (isFilled) 1f else 0.4f),
                shape = CircleShape
            )
    )
}

@Composable
private fun NumberPad(
    onNumberClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    isDarkTheme: Boolean,
    buttonContainerColor: Color,
    buttonContentColor: Color
) {
    val numberButtonColor = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
    val numberTextColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 24.dp)
    ) {
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        ).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { number ->
                    NumberButton(
                        text = number,
                        onClick = { onNumberClick(number) },
                        modifier = Modifier.weight(1f),
                        containerColor = numberButtonColor,
                        contentColor = numberTextColor
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.weight(1f))
            NumberButton(
                text = "0",
                onClick = { onNumberClick("0") },
                modifier = Modifier.weight(1f),
                containerColor = numberButtonColor,
                contentColor = numberTextColor
            )
            Surface(
                onClick = onDeleteClick,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
                shape = RoundedCornerShape(16.dp),
                color = buttonContainerColor.copy(alpha = 0.1f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.Backspace,
                        contentDescription = "Delete",
                        modifier = Modifier.size(24.dp),
                        tint = numberTextColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color,
    contentColor: Color
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        color = containerColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = text,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
    }
}
