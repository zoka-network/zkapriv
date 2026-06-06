package com.zoka.zkapriv.ui.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wallet entry screen (create / recover). Styled to match the biometric
 * LockScreen: a decorative top gradient band, the white ZOKA NETWORK wordmark,
 * a version label, a bold heading, and the primary actions anchored to the
 * bottom — with theme-aware black/white buttons and a soft entrance animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreenNew(
    onCreateWallet: () -> Unit,
    onImportWallet: () -> Unit
) {
    var showTermsDialog by remember { mutableStateOf(false) }
    var acceptedTerms by remember { mutableStateOf(false) }

    // Theme detection based on background luminance (same approach as LockScreen).
    val backgroundColor = MaterialTheme.colorScheme.background
    val isDarkTheme = remember(backgroundColor) {
        (backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f) < 0.5f
    }

    val buttonContainerColor = if (isDarkTheme) Color.White else Color.Black
    val buttonContentColor = if (isDarkTheme) Color.Black else Color.White
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val accent = MaterialTheme.colorScheme.primary

    // Decorative top gradient band: black→transparent (light), white→transparent (dark).
    val gradientBrush = Brush.verticalGradient(
        colors = if (isDarkTheme)
            listOf(Color.White.copy(alpha = 0.16f), backgroundColor.copy(alpha = 0f))
        else
            listOf(Color.Black.copy(alpha = 0.90f), backgroundColor.copy(alpha = 0f))
    )

    // Entrance animation (mirrors LockScreen).
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
            Spacer(modifier = Modifier.height(64.dp))

            // ZOKA NETWORK wordmark, white over the dark gradient band.
            Image(
                painter = painterResource(id = com.zoka.zkapriv.ui.R.drawable.logoportada),
                contentDescription = "Zoka Network",
                modifier = Modifier
                    .height(60.dp)
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                    },
                colorFilter = ColorFilter.tint(Color.White)
            )

            Spacer(modifier = Modifier.height(72.dp))

            Text(
                text = "Your private wallet",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "ZK Privacy · Self-Custodial · Zoka Network",
                fontSize = 15.sp,
                color = textSecondary
            )

            // Push the actions to the bottom of the screen.
            Spacer(modifier = Modifier.weight(1f))

            // Primary: Create New Wallet (theme-aware black/white).
            Button(
                onClick = {
                    if (acceptedTerms) onCreateWallet() else showTermsDialog = true
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
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Create New Wallet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary: Recover / Import Wallet (theme-aware outline).
            OutlinedButton(
                onClick = {
                    if (acceptedTerms) onImportWallet() else showTermsDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                border = BorderStroke(2.dp, textPrimary.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Recover Wallet", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Terms acceptance.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = acceptedTerms,
                    onCheckedChange = { acceptedTerms = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = accent,
                        uncheckedColor = textSecondary,
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "I accept the",
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                TextButton(
                    onClick = { showTermsDialog = true },
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = "Terms & Conditions",
                        color = accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = { showTermsDialog = true }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = "View Terms",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Zoka Network · MainNet",
                    color = textSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Developed by Natosh Zoka",
                    color = textPrimary.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Terms Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "Terms & Conditions",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Please read and accept our terms before proceeding:",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "• You are responsible for keeping your recovery phrase safe\n" +
                                "• Never share your seed phrase or scan key with anyone\n" +
                                "• Transactions on the Zoka blockchain are irreversible\n" +
                                "• This wallet is self-custodial — Zoka Network has no access to your keys\n" +
                                "• Zoka uses ZK (zero-knowledge) proofs for transaction privacy\n" +
                                "• Private transactions are confidential by design — balances and amounts are not publicly visible",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        acceptedTerms = true
                        showTermsDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonContainerColor,
                        contentColor = buttonContentColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Accept")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTermsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = textPrimary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
