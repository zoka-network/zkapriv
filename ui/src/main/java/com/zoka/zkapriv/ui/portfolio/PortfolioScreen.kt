package com.zoka.zkapriv.ui.portfolio

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.zoka.zkapriv.core.model.Account
import com.zoka.zkapriv.core.model.AccountColor
import com.zoka.zkapriv.core.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onClose: () -> Unit,
    viewModel: PortfolioViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val backgroundColor = MaterialTheme.colorScheme.background
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val isDarkTheme = remember(backgroundColor) {
        (backgroundColor.red * 0.299f + backgroundColor.green * 0.587f + backgroundColor.blue * 0.114f) < 0.5f
    }

    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "shimmerAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "All Accounts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            "${uiState.accounts.size} wallet${if (uiState.accounts.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textPrimary,
                    navigationIconContentColor = textPrimary
                )
            )
        },
        containerColor = backgroundColor
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Privacy banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0D0D1A), Color(0xFF1A0D2E))
                                ),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF7B1FA2).copy(alpha = 0.3f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFFCE93D8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "ZK Privacy — Each account is independent",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Accounts are not linked on-chain. Tap an account to make it active.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isLoading && uiState.accounts.isEmpty()) {
                items(2) {
                    AccountShimmerCard(shimmerAlpha, cardColor, textSecondary)
                }
            } else if (uiState.error != null) {
                item {
                    ErrorCard(uiState.error!!, textPrimary, textSecondary, isDarkTheme) {
                        viewModel.refresh()
                    }
                }
            } else if (uiState.accounts.isEmpty()) {
                item {
                    EmptyAccountsCard(textPrimary, textSecondary, isDarkTheme)
                }
            } else {
                items(uiState.accounts, key = { it.account.id }) { summary ->
                    AccountCard(
                        summary = summary,
                        cardColor = cardColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        isDarkTheme = isDarkTheme,
                        shimmerAlpha = shimmerAlpha,
                        onSelect = {
                            viewModel.switchToAccount(summary.account.id)
                            onClose()
                        },
                        onCopyAddress = {
                            clipboardManager.setText(AnnotatedString(summary.account.address))
                        },
                        onViewExplorer = {
                            val url = "${Constants.ZOKA_EXPLORER_ACCOUNT_URL}/${summary.account.address}"
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse(url)
                            )
                            context.startActivity(intent)
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun AccountCard(
    summary: AccountSummary,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    isDarkTheme: Boolean,
    shimmerAlpha: Float,
    onSelect: () -> Unit,
    onCopyAddress: () -> Unit,
    onViewExplorer: () -> Unit
) {
    val account = summary.account
    val accountColor = try {
        Color(android.graphics.Color.parseColor(account.color.hex))
    } catch (e: Exception) {
        Color(0xFF2196F3)
    }

    var showCopied by remember { mutableStateOf(false) }
    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1200)
            showCopied = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (account.isActive) 6.dp else if (isDarkTheme) 0.dp else 2.dp
        ),
        border = if (account.isActive)
            androidx.compose.foundation.BorderStroke(2.dp, accountColor)
        else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color dot with initial
                    Surface(
                        shape = CircleShape,
                        color = accountColor.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                account.name.first().uppercaseChar().toString(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = accountColor
                            )
                        }
                    }
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                account.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = textPrimary
                            )
                            if (account.isActive) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = accountColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        "Active",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = accountColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (account.address.length >= 10)
                                "${account.address.take(8)}...${account.address.takeLast(6)}"
                            else account.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Balance
                Column(horizontalAlignment = Alignment.End) {
                    if (summary.isLoadingBalance) {
                        Box(
                            modifier = Modifier
                                .width(90.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(textSecondary.copy(alpha = shimmerAlpha * 0.3f))
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(60.dp)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(textSecondary.copy(alpha = shimmerAlpha * 0.2f))
                        )
                    } else {
                        Text(
                            "${summary.matureBalance} ZKA",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = textPrimary
                        )
                        if (summary.hasMining) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = "Maturing",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(10.dp)
                                )
                                Text(
                                    "${summary.immatureBalance} maturing",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFFF9800)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Divider(color = textSecondary.copy(alpha = 0.08f))
            Spacer(Modifier.height(12.dp))

            // Actions row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy address
                TextButton(
                    onClick = {
                        onCopyAddress()
                        showCopied = true
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (showCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp),
                        tint = if (showCopied) Color(0xFF4CAF50) else textSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (showCopied) "Copied" else "Copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showCopied) Color(0xFF4CAF50) else textSecondary
                    )
                }

                Spacer(Modifier.width(4.dp))

                // View in explorer
                TextButton(
                    onClick = onViewExplorer,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        Icons.Outlined.OpenInNew,
                        contentDescription = "Explorer",
                        modifier = Modifier.size(14.dp),
                        tint = textSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Explorer",
                        style = MaterialTheme.typography.labelSmall,
                        color = textSecondary
                    )
                }

                if (!account.isActive) {
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        onClick = onSelect,
                        shape = RoundedCornerShape(10.dp),
                        color = accountColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Activate",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accountColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountShimmerCard(shimmerAlpha: Float, cardColor: Color, shimmerColor: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(shimmerColor.copy(alpha = shimmerAlpha * 0.3f))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .background(shimmerColor.copy(alpha = shimmerAlpha * 0.3f), RoundedCornerShape(6.dp))
                )
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(12.dp)
                        .background(shimmerColor.copy(alpha = shimmerAlpha * 0.2f), RoundedCornerShape(4.dp))
                )
            }
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(16.dp)
                    .background(shimmerColor.copy(alpha = shimmerAlpha * 0.3f), RoundedCornerShape(6.dp))
            )
        }
    }
}

@Composable
private fun EmptyAccountsCard(textPrimary: Color, textSecondary: Color, isDarkTheme: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = Color(0xFF7B1FA2).copy(alpha = 0.15f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFF7B1FA2),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Text(
                "No Accounts",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = textPrimary
            )
            Text(
                "Create or import a wallet to get started.",
                color = textSecondary,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ErrorCard(
    error: String,
    textPrimary: Color,
    textSecondary: Color,
    isDarkTheme: Boolean,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(40.dp)
            )
            Text(error, color = textSecondary, textAlign = TextAlign.Center)
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDarkTheme) Color.White else Color.Black,
                    contentColor = if (isDarkTheme) Color.Black else Color.White
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Retry", fontWeight = FontWeight.Bold)
            }
        }
    }
}
