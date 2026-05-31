package com.zoka.zkapriv.ui.lock

import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay

/**
 * Typewriter effect composable for animated text display
 */
@Composable
fun TypewriterText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight,
    color: Color,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
    delayPerChar: Long = 80L,
    initialDelay: Long = 500L
) {
    var displayedText by remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayedText = ""
        delay(initialDelay)
        text.forEachIndexed { index, _ ->
            displayedText = text.substring(0, index + 1)
            delay(delayPerChar)
        }
    }
    
    Text(
        text = displayedText,
        fontSize = fontSize,
        fontWeight = fontWeight,
        color = color,
        letterSpacing = letterSpacing,
        modifier = modifier
    )
}

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
    
    // Consistent colors
    val buttonContainerColor = if (isDarkTheme) Color.White else Color.Black
    val buttonContentColor = if (isDarkTheme) Color.Black else Color.White
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    
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
            .setNegativeButtonText("Use PIN Instead")
            .build()
    }
    
    LaunchedEffect(uiState.showBiometricPrompt) {
        if (uiState.showBiometricPrompt) {
            biometricPrompt.authenticate(promptInfo)
            viewModel.resetBiometricPrompt()
        }
    }
    
    LaunchedEffect(uiState.isUnlocked) {
        if (uiState.isUnlocked) {
            onUnlocked()
        }
    }

    LaunchedEffect(uiState.walletReset) {
        if (uiState.walletReset) {
            onWalletReset()
        }
    }
    
    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    val logoScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.5f,
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
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Version text at top right
        Text(
            text = "v0.1.0",
            fontSize = 12.sp,
            color = textSecondary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 24.dp)
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .graphicsLayer { alpha = contentAlpha },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top section — official ZOKA NETWORK wordmark in place of the
            // legacy circular badge. Height-only sizing preserves the
            // horizontal aspect ratio of the LOGOPORTADA artwork. The
            // entrance scale animation still runs so the screen retains
            // the same arrival feel.
            val logoHeight = if (showPinInput) 48.dp else 96.dp
            val topPadding = if (showPinInput) 16.dp else 40.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = topPadding)
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(id = com.zoka.zkapriv.ui.R.drawable.logoportada),
                    contentDescription = "Zoka Network",
                    modifier = Modifier
                        .height(logoHeight)
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                        },
                    colorFilter = if (isDarkTheme)
                        androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                    else
                        null
                )

                Spacer(modifier = Modifier.height(if (showPinInput) 16.dp else 28.dp))

                // App name with typewriter effect (only when not in PIN mode)
                if (!showPinInput) {
                    TypewriterText(
                        text = "zkapriv",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary,
                        letterSpacing = (-1).sp,
                        delayPerChar = 100L,
                        initialDelay = 300L
                    )
                } else {
                    Text(
                        text = "zkapriv",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = textPrimary,
                        letterSpacing = (-1).sp
                    )
                }
                
                // Only show tagline when not in PIN mode
                if (!showPinInput) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Privacy tagline pill — icon + label communicates the
                    // wallet's positioning without needing a second logo.
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.PrivacyTip,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = textPrimary
                            )
                            Text(
                                text = "Private Wallet",
                                fontSize = 14.sp,
                                color = textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Middle section - Authentication
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (uiState.biometricAvailable && !showPinInput) {
                    // Biometric unlock section
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
                            // Fingerprint icon with pulse animation
                            val infiniteTransition = rememberInfiniteTransition(label = "biometric_pulse")
                            val pulseScale by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "pulse"
                            )
                            
                            Surface(
                                modifier = Modifier
                                    .size(80.dp)
                                    .graphicsLayer {
                                        scaleX = pulseScale
                                        scaleY = pulseScale
                                    },
                                shape = CircleShape,
                                color = buttonContainerColor
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp),
                                        tint = buttonContentColor
                                    )
                                }
                            }
                            
                            Text(
                                text = "Tap to Unlock",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textPrimary
                            )
                            
                            // Biometric unlock button
                            Button(
                                onClick = { viewModel.authenticateWithBiometric() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp),
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
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Unlock with Biometric",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = { 
                            showPinInput = true
                            viewModel.clearError()
                        }
                    ) {
                        Text(
                            "Use PIN Instead",
                            fontSize = 15.sp,
                            color = textSecondary
                        )
                    }

                    ForgotPasswordButton(onClick = { viewModel.showForgotPasswordDialog(true) })
                } else {
                    // PIN unlock section
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
                            // Lock icon
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
                            
                            // PIN dots
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
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
                                if (pin.isEmpty()) {
                                    viewModel.clearError()
                                }
                                pin += number
                                if (pin.length == 6) {
                                    viewModel.verifyPin(pin)
                                    if (uiState.error != null) {
                                        pin = ""
                                    }
                                }
                            }
                        },
                        onDeleteClick = {
                            if (pin.isNotEmpty()) {
                                pin = pin.dropLast(1)
                            }
                        },
                        isDarkTheme = isDarkTheme,
                        buttonContainerColor = buttonContainerColor,
                        buttonContentColor = buttonContentColor
                    )

                    ForgotPasswordButton(onClick = { viewModel.showForgotPasswordDialog(true) })

                    if (uiState.biometricAvailable) {
                        TextButton(
                            onClick = { 
                                showPinInput = false
                                viewModel.clearError()
                                pin = ""
                            }
                        ) {
                            Text(
                                "Use Biometric Instead",
                                fontSize = 14.sp,
                                color = textSecondary
                            )
                        }
                    }
                }
            }

            // Bottom spacer
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Reset wallet dialog
        if (uiState.showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!uiState.isLoading) {
                        viewModel.showForgotPasswordDialog(false)
                    }
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
                    Text(
                        text = "Reset wallet?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
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
                        onClick = {
                            viewModel.resetWallet()
                        },
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
    
    // Animation for fill state
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
        // Rows 1-3
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

        // Row 4: Empty, 0, Delete
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
            // Delete button with icon
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
private fun ForgotPasswordButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.LockReset,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Can't access your wallet?",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Reset with your recovery phrase",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
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
