package com.zoka.zkapriv.ui.transaction

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendScreen(
    onClose: () -> Unit,
    onScanQr: () -> Unit,
    onTransactionSuccess: () -> Unit = {},
    viewModel: SendViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isDarkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    // Consistent button colors with onboarding
    val buttonColor = if (isDarkTheme) Color(0xFF2A2A2A) else Color.Black
    val buttonTextColor = Color.White
    
    // Biometric authentication
    val biometricPrompt = remember {
        BiometricPrompt(
            context as FragmentActivity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.onAuthenticationSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled or error occurred
                }
                
                override fun onAuthenticationFailed() {
                    // Biometric not recognized
                }
            }
        )
    }
    
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authorize Transaction")
            .setSubtitle("Confirm sending ${uiState.amount} ZKA")
            .setNegativeButtonText("Use PIN")
            .build()
    }
    
    // Listen for biometric request
    LaunchedEffect(Unit) {
        viewModel.biometricRequest.collect {
            biometricPrompt.authenticate(promptInfo)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Send ZKA",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            // Sticky Send button pinned above the IME so it stays reachable
            // even when the keyboard pushes the recipient + amount fields up.
            val buttonScale by animateFloatAsState(
                targetValue = if (uiState.isValidAmount && uiState.isValidAddress) 1f else 0.95f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                ),
                label = "buttonScale"
            )
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                FilledTonalButton(
                    onClick = { viewModel.requestAuthentication() },
                    enabled = uiState.isValidAmount && uiState.isValidAddress && !uiState.isLoading
                        && uiState.isBalanceVerified
                        && uiState.recipientAddress.isNotBlank() && uiState.amount.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(64.dp)
                        .graphicsLayer {
                            scaleX = buttonScale
                            scaleY = buttonScale
                        },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = buttonColor,
                        contentColor = buttonTextColor,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = buttonTextColor
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Send,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Send ZKA",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Balance Card with animation
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Available Balance",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${uiState.availableBalance} ZKA",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Dos avisos distintos, porque son dos situaciones distintas.
            //
            // Sin anclar a la cadena: el saldo no es confiable y no se puede
            // gastar. Eso es un error y va en rojo.
            //
            // Con cuerpos faltantes pero anclado: el saldo ES gastable, solo que
            // podria haber dinero entrante que no vemos. Antes este bloque se
            // mostraba solo cuando el saldo no estaba verificado, asi que al
            // dejar de bloquear el envio el aviso desaparecia entero y el
            // usuario perdia la unica senal de que su saldo podia estar
            // incompleto.
            if (!uiState.isLoading && (!uiState.isBalanceVerified || uiState.isBalanceIncomplete)) {
                val esBloqueante = !uiState.isBalanceVerified
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (esBloqueante) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            Color(0xFFFF9800).copy(alpha = 0.12f)
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (esBloqueante) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                Color(0xFFFF9800)
                            }
                        )
                        Text(
                            // El texto viejo decia "Sending is disabled" para el
                            // caso de cuerpos faltantes, y ya no es cierto: un
                            // cuerpo que falta solo puede ocultar dinero
                            // entrante, nunca inflar el saldo, asi que lo que se
                            // ve se puede gastar.
                            text = uiState.balanceError ?: if (uiState.missingTxCount > 0) {
                                "${uiState.missingTxCount} private transaction " +
                                    "${if (uiState.missingTxCount == 1) "body is" else "bodies are"} " +
                                    "unavailable from the P2P network, so any funds they may have " +
                                    "sent you are not counted here. You can still spend the balance shown."
                            } else {
                                "Balance has not been verified at the current P2P chain tip. Sending is disabled."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (esBloqueante) {
                                MaterialTheme.colorScheme.onErrorContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            // Amount Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.Payments,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Amount to Send",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Amount input with MAX button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = if (uiState.isUsdMode) uiState.usdAmount else uiState.amount,
                            onValueChange = { viewModel.updateAmount(it) },
                            label = { Text(if (uiState.isUsdMode) "Amount (USD)" else "Amount (ZKA)") },
                            placeholder = { Text("0.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            isError = !uiState.isValidAmount,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                cursorColor = MaterialTheme.colorScheme.onSurface
                            ),
                            trailingIcon = {
                                // Currency toggle button
                                TextButton(
                                    onClick = { viewModel.toggleCurrency() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        if (uiState.isUsdMode) "USD" else "ZKA",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                        
                        // MAX button - smaller and aligned
                        Button(
                            onClick = { viewModel.setMaxAmount() },
                            enabled = uiState.isBalanceVerified && !uiState.isLoading,
                            modifier = Modifier
                                .height(48.dp)
                                .width(58.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = buttonColor,
                                contentColor = buttonTextColor
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                "MAX",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    
                    AnimatedVisibility(
                        visible = (uiState.isUsdMode && uiState.usdAmount.isNotEmpty()) || 
                                  (!uiState.isUsdMode && uiState.amount.isNotEmpty()),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (!uiState.isValidAmount && uiState.isBalanceVerified) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Error,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "Insufficient balance",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else {
                                // Show conversion: if USD mode, show ZKA equivalent; if ZKA mode, show USD equivalent
                                Text(
                                    if (uiState.isUsdMode) "≈ ${uiState.amount} ZKA" else "≈ $${uiState.usdAmount} USD",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Recipient Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.PersonOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Recipient",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    OutlinedTextField(
                        value = uiState.recipientAddress,
                        onValueChange = { viewModel.updateAddress(it) },
                        label = { Text("Zoka address") },
                        placeholder = { Text("zka1...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        // singleLine prevents the field from wrapping the
                        // ~95-char zka1 address vertically and pushing the
                        // rest of the screen down. The address scrolls
                        // horizontally inside the field instead.
                        singleLine = true,
                        leadingIcon = {
                            // Live validation indicator at the start of
                            // the field. Empty input → neutral; non-empty
                            // valid → green check; non-empty invalid → red
                            // error. The whole-line border still flips red
                            // via `isError` for color-blind users.
                            val (icon, tint, desc) = when {
                                uiState.recipientAddress.isEmpty() -> Triple(
                                    Icons.Outlined.AccountCircle,
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    "Address"
                                )
                                uiState.isValidAddress -> Triple(
                                    Icons.Outlined.CheckCircle,
                                    Color(0xFF22C55E),
                                    "Valid Zoka address"
                                )
                                else -> Triple(
                                    Icons.Outlined.Cancel,
                                    MaterialTheme.colorScheme.error,
                                    "Invalid Zoka address"
                                )
                            }
                            Icon(icon, contentDescription = desc, tint = tint)
                        },
                        trailingIcon = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Paste button
                                IconButton(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { pastedText ->
                                            viewModel.updateAddress(pastedText.trim())
                                        }
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ContentPaste,
                                        "Paste",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // QR Scanner button
                                IconButton(onClick = onScanQr) {
                                    Icon(
                                        Icons.Outlined.QrCodeScanner,
                                        "Scan QR",
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        isError = !uiState.isValidAddress,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedLabelColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            cursorColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    
                    AnimatedVisibility(
                        visible = !uiState.isValidAddress && uiState.recipientAddress.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Error,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Invalid Zoka address",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // Send button now lives in the Scaffold bottomBar above so it
            // stays sticky and visible even when the keyboard pushes the
            // recipient + amount inputs up. Leaving 12dp at the bottom of
            // the scrollable content so the last card never feels glued
            // to the button.
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Authentication dialog
        if (uiState.showAuthDialog) {
            com.zoka.zkapriv.ui.auth.AuthenticationDialog(
                title = "Authorize Transaction",
                subtitle = "Confirm sending ${uiState.amount} ZKA",
                biometricEnabled = viewModel.getBiometricEnabled(),
                onAuthenticated = { viewModel.onAuthenticationSuccess() },
                onDismiss = { viewModel.onAuthenticationCancelled() },
                onVerifyPin = viewModel::verifyPin,
                onBiometricRequest = { viewModel.requestBiometric() }
            )
        }

        // Success dialog - Removed, using TransactionSuccessScreen instead

        // Error dialog
        uiState.error?.let { error ->
            AlertDialog(
                onDismissRequest = { },
                title = { 
                    Text(
                        "Error",
                        color = MaterialTheme.colorScheme.onSurface
                    ) 
                },
                text = { 
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    ) 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.dismissResultScreen()
                            viewModel.requestAuthentication()
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text("Retry")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onClose,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    ) {
                        Text("Cancel")
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface
            )
        }
    }
    
    // Success screen overlay
    if (uiState.showSuccessScreen) {
        TransactionSuccessScreen(
            amount = uiState.amount,
            recipient = uiState.recipientAddress,
            onDismiss = {
                viewModel.dismissResultScreen()
                onTransactionSuccess() // Refresh dashboard before closing
                onClose()
            }
        )
    }
    
    // Failure screen overlay
    if (uiState.showFailureScreen) {
        TransactionFailureScreen(
            errorMessage = uiState.error ?: "Unknown error occurred",
            onDismiss = {
                viewModel.dismissResultScreen()
            }
        )
    }
}
