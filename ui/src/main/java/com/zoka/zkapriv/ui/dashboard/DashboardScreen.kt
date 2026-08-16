package com.zoka.zkapriv.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zoka.zkapriv.core.model.Transaction
import com.zoka.zkapriv.core.model.TransactionStatus
import com.zoka.zkapriv.ui.components.LucideIcons
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onSendClick: () -> Unit,
    onReceiveClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onQrScanClick: () -> Unit = {},
    onChartsClick: () -> Unit = {},
    onAccountsClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    
    // Parse account color from hex
    val accountColor = try {
        Color(android.graphics.Color.parseColor(uiState.activeAccountColor))
    } catch (e: Exception) {
        Color(0xFF2196F3)
    }
    
    // Use MaterialTheme colors for consistent theming
    val backgroundColor = MaterialTheme.colorScheme.background
    val cardBackground = MaterialTheme.colorScheme.surfaceVariant
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val iconTint = MaterialTheme.colorScheme.onBackground
    
    // Accent colors - theme-aware for better visibility
    // Detect dark theme by checking if background is black (or very dark)
    val isDarkTheme = backgroundColor == Color.Black || backgroundColor == Color(0xFF000000)
    val web3Purple = if (isDarkTheme) Color(0xFF9B6BFF) else Color(0xFF6A1B9A)
    val web3Cyan = if (isDarkTheme) Color(0xFF4DD0E1) else Color(0xFF0097A7)
    val web3Red = if (isDarkTheme) Color(0xFFFF6B6B) else Color(0xFFD32F2F)
    val web3Green = if (isDarkTheme) Color(0xFF4CAF50) else Color(0xFF2E7D32)
    
    // Shimmer animation for loading state
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by shimmerTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    val clipboardManager = LocalClipboardManager.current
    var showCopiedToast by remember { mutableStateOf(false) }
    
    // Auto-refresh when screen becomes visible (returning from Portfolio, Send, etc.)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDataIfStale()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Show copied toast
    LaunchedEffect(showCopiedToast) {
        if (showCopiedToast) {
            kotlinx.coroutines.delay(1500)
            showCopiedToast = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            "zkapriv",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = textPrimary
                        )
                        // Account indicator with address - clickable to switch accounts
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onAccountsClick)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(accountColor)
                            )
                            Text(
                                text = uiState.activeAccountName,
                                style = MaterialTheme.typography.bodySmall,
                                color = textSecondary
                            )
                            // Wallet address inline
                            uiState.activeWallet?.let { wallet ->
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = "${wallet.take(6)}...${wallet.takeLast(4)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary.copy(alpha = 0.7f)
                                )
                                // Copy button
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(wallet))
                                            showCopiedToast = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (showCopiedToast) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        modifier = Modifier.size(12.dp),
                                        tint = if (showCopiedToast) Color(0xFF4CAF50) else textSecondary.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            if (uiState.accountCount > 1) {
                                Text(
                                    text = "(${uiState.accountCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = textSecondary.copy(alpha = 0.5f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Switch Account",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = textPrimary
                ),
                actions = {
                    IconButton(onClick = onQrScanClick) {
                        Icon(
                            imageVector = LucideIcons.Scan,
                            contentDescription = "Scan QR",
                            tint = iconTint
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Modern bottom navigation for the core wallet flow
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = if (isDarkTheme) Color(0xFF0D0D15) else Color.White,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ModernNavItem(
                        icon = LucideIcons.House,
                        label = "Home",
                        isSelected = selectedTab == 0,
                        isDarkTheme = isDarkTheme,
                        onClick = { selectedTab = 0 }
                    )
                    ModernNavItem(
                        icon = Icons.Outlined.ArrowDownward,
                        label = "Receive",
                        isSelected = selectedTab == 1,
                        isDarkTheme = isDarkTheme,
                        onClick = { 
                            selectedTab = 1
                            onReceiveClick()
                        }
                    )
                    ModernNavItem(
                        icon = LucideIcons.SendHorizontal,
                        label = "Send",
                        isSelected = selectedTab == 2,
                        isDarkTheme = isDarkTheme,
                        onClick = { 
                            selectedTab = 2
                            onSendClick()
                        }
                    )
                    ModernNavItem(
                        icon = LucideIcons.Settings,
                        label = "Settings",
                        isSelected = selectedTab == 3,
                        isDarkTheme = isDarkTheme,
                        onClick = { 
                            selectedTab = 3
                            onSettingsClick()
                        }
                    )
                }
            }
        },
        containerColor = backgroundColor,
        contentColor = Color.Transparent
    ) { padding ->
        // Pull to refresh indicator at top when loading
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Balance Card - Web3 Style with Charts button
                item {
                    Web3BalanceCard(
                        balance = uiState.balance.toDoubleOrNull() ?: 0.0,
                        availableBalance = uiState.availableBalance,
                        immatureBalance = uiState.immatureBalance,
                        pendingOutBalance = uiState.pendingOutBalance,
                        hasPendingOut = uiState.hasPendingOut,
                        hasMining = uiState.hasMining,
                        usdValue = uiState.usdValue.toDoubleOrNull() ?: 0.0,
                        totalPortfolioValue = uiState.totalPortfolioValue.toDouble(),
                        currentPrice = uiState.currentPrice,
                        priceChange24h = uiState.priceChange24h,
                        isLoading = uiState.isLoading,
                        isBalanceHidden = uiState.isBalanceHidden,
                        shimmerAlpha = shimmerAlpha,
                        cardBackground = cardBackground,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentColor = web3Cyan,
                        onChartsClick = onChartsClick,
                        onPortfolioClick = onPortfolioClick,
                        onToggleVisibility = viewModel::toggleBalanceVisibility,
                        zokaStats = uiState.zokaStats,
                        isBalanceIncomplete = uiState.isBalanceIncomplete,
                        isBalanceVerified = uiState.isBalanceVerified,
                        balanceVerificationIssue = uiState.balanceVerificationIssue,
                        missingTxCount = uiState.missingTxCount,
                        syncing = uiState.syncing,
                        syncFraction = uiState.syncFraction,
                        syncScannedHeight = uiState.syncScannedHeight,
                        syncChainHeight = uiState.syncChainHeight
                    )
                }

                // Quick Actions Grid
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickActionCard(
                                icon = Icons.Outlined.ArrowUpward,
                                label = "Send",
                                onClick = onSendClick,
                                color = web3Purple,
                                cardBackground = cardBackground,
                                textColor = textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionCard(
                                icon = Icons.Outlined.ArrowDownward,
                                label = "Receive",
                                onClick = onReceiveClick,
                                color = web3Cyan,
                                cardBackground = cardBackground,
                                textColor = textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            QuickActionCard(
                                icon = Icons.Outlined.AccountCircle,
                                label = "Accounts",
                                onClick = onAccountsClick,
                                color = web3Green,
                                cardBackground = cardBackground,
                                textColor = textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            QuickActionCard(
                                icon = Icons.Outlined.Shield,
                                label = "All Accounts",
                                onClick = onPortfolioClick,
                                color = Color(0xFF7B1FA2),
                                cardBackground = cardBackground,
                                textColor = textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Transactions Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Activity",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            ),
                            color = textPrimary
                        )
                        if (uiState.recentTransactions.isNotEmpty()) {
                            TextButton(onClick = { /* TODO */ }) {
                                Text("View All", color = web3Purple)
                            }
                        }
                    }
                }

                // Transactions List
                if (uiState.isLoading) {
                    items(3) {
                        Web3ShimmerTransaction(shimmerAlpha, cardBackground, textSecondary)
                    }
                } else if (uiState.recentTransactions.isEmpty()) {
                    item {
                        Web3EmptyState(web3Purple, cardBackground, textPrimary, textSecondary)
                    }
                } else {
                    itemsIndexed(
                        items = uiState.recentTransactions,
                        key = { index, tx -> "${tx.hash}_$index" }
                    ) { _, transaction ->
                        Web3TransactionItem(
                            transaction = transaction,
                            walletAddress = uiState.activeWallet ?: "",
                            accentColorReceive = web3Cyan,
                            accentColorSent = web3Red,
                            accentColorSuccess = web3Green,
                            cardBackground = cardBackground,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single-row breakdown line under the big ZKA balance counter. Used to
 * show Available / Pending out so the user understands that "Total" is
 * not the same as what they can actually spend right now.
 */
@Composable
private fun BalanceBreakdownRow(
    label: String,
    value: String,
    valueColor: Color,
    labelColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = labelColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = valueColor
        )
    }
}

@Composable
private fun Web3BalanceCard(
    balance: Double,
    availableBalance: String,
    immatureBalance: String,
    pendingOutBalance: String,
    hasPendingOut: Boolean,
    hasMining: Boolean,
    usdValue: Double,
    totalPortfolioValue: Double,
    currentPrice: Double,
    priceChange24h: Double,
    isLoading: Boolean,
    isBalanceHidden: Boolean,
    shimmerAlpha: Float,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentColor: Color,
    onChartsClick: () -> Unit,
    onPortfolioClick: () -> Unit,
    onToggleVisibility: () -> Unit,
    zokaStats: com.zoka.zkapriv.price.model.ZokaStats?,
    isBalanceIncomplete: Boolean = false,
    isBalanceVerified: Boolean = false,
    balanceVerificationIssue: String? = null,
    missingTxCount: Int = 0,
    syncing: Boolean = false,
    syncFraction: Float? = null,
    syncScannedHeight: Long = 0,
    syncChainHeight: Long = 0
) {
    val displayUsdValue = if (totalPortfolioValue > 0) totalPortfolioValue else usdValue
    val hasUsdPrice = currentPrice > 0.0
    val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    // Button colors that work in both themes
    val iconButtonBg = if (isDarkTheme) {
        Color(0xFF1A1A1A)
    } else {
        Color(0xFFEEEEEE) // Light gray in light mode
    }
    val iconButtonTint = if (isDarkTheme) {
        Color.White
    } else {
        Color(0xFF333333) // Dark gray in light mode
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDarkTheme) 0.dp else 4.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Section - Title with Eye Button and View Charts Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Total Balance text with eye icon next to it
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Total Balance",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textSecondary
                        )
                        
                        // Eye icon without container - respects dark/light theme
                        IconButton(
                            onClick = onToggleVisibility,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isBalanceHidden) "Show balance" else "Hide balance",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // View Charts Button
                    IconButton(
                        onClick = onChartsClick,
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = iconButtonBg,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = "View Charts",
                            tint = iconButtonTint,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                // ZKA Balance — primary display (large)
                if (isLoading) {
                    com.zoka.zkapriv.ui.components.ShimmerBox(
                        width = 200.dp,
                        height = 52.dp
                    )
                } else {
                    if (isBalanceHidden) {
                        Text(
                            text = "•••••• ZKA",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 38.sp,
                                letterSpacing = 4.sp
                            ),
                            color = textPrimary
                        )
                    } else {
                        val cryptoDecimals = remember(balance) {
                            if (balance % 1.0 < 0.0000005) 0 else 2
                        }
                        com.zoka.zkapriv.ui.components.AnimatedCryptoCounter(
                            targetValue = balance,
                            symbol = "ZKA",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                fontSize = 38.sp
                            ),
                            color = textPrimary,
                            decimals = cryptoDecimals
                        )
                    }
                }

                // USD value — secondary display (small) + Portfolio button
                if (isLoading) {
                    com.zoka.zkapriv.ui.components.ShimmerBox(
                        width = 120.dp,
                        height = 20.dp
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isBalanceHidden) {
                            Text(
                                text = "$ ••••",
                                style = MaterialTheme.typography.bodyMedium,
                                color = textSecondary
                            )
                        } else {
                            // USD valuation is parked until a price feed lands.
                            // Show "Soon" with a clock icon instead of a stale $ ---.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Schedule,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$ Soon",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                    ),
                                    color = textSecondary
                                )
                            }
                        }

                        // Portfolio Button
                        Surface(
                            onClick = onPortfolioClick,
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = iconButtonBg
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Portfolio",
                                    tint = iconButtonTint,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    "Portfolio",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = iconButtonTint
                                )
                            }
                        }
                    }
                }

                // Three-line breakdown so the user can distinguish what's
                // mature on chain vs locked in pending outbound sends.
                // Visible whenever the balance isn't hidden — Available
                // is the line that matters for "what can I spend right now".
                if (!isLoading && !isBalanceHidden) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BalanceBreakdownRow(
                            label = "Available",
                            value = "$availableBalance ZKA",
                            valueColor = textPrimary,
                            labelColor = textSecondary
                        )
                        if (hasPendingOut) {
                            BalanceBreakdownRow(
                                label = "Pending out",
                                value = "$pendingOutBalance ZKA",
                                valueColor = Color(0xFFFFC107),
                                labelColor = textSecondary
                            )
                        }
                    }
                }

                // Senal de vida del escaneo.
                //
                // La tarjeta ya avisaba cuando el saldo no estaba verificado --
                // el resultado -- pero nunca mostraba el proceso, asi que una
                // espera larga y una pantalla trabada se veian igual. Esto es
                // deliberadamente discreto: una linea fina y texto secundario,
                // porque acompana al saldo, no compite con el.
                if (syncing && !isBalanceHidden) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Syncing with the network",
                                style = MaterialTheme.typography.labelSmall,
                                color = textSecondary
                            )
                            // El porcentaje solo cuando se sabe hasta donde hay
                            // que llegar: un numero inventado es peor que
                            // ninguno.
                            syncFraction?.let { avance ->
                                Text(
                                    text = "${(avance * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textSecondary
                                )
                            }
                        }
                        if (syncFraction != null) {
                            LinearProgressIndicator(
                                progress = syncFraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = accentColor,
                                trackColor = textSecondary.copy(alpha = 0.15f)
                            )
                        } else {
                            // Sin fraccion conocida, una barra indeterminada:
                            // sigue diciendo "estoy trabajando" sin afirmar
                            // cuanto falta.
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = accentColor,
                                trackColor = textSecondary.copy(alpha = 0.15f)
                            )
                        }
                        if (syncChainHeight > 0) {
                            Text(
                                text = "Block $syncScannedHeight of $syncChainHeight",
                                style = MaterialTheme.typography.labelSmall,
                                color = textSecondary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Dos avisos distintos, porque son dos problemas distintos.
                //
                // Sin anclar a la cadena: el saldo no es confiable y no se puede
                // gastar -- eso es un error, va en rojo.
                //
                // Con cuerpos faltantes pero anclado: el saldo es gastable, solo
                // que podria haber dinero entrante que no vemos. Eso es una
                // advertencia, no un error, y pintarlo de rojo hacia que pareciera
                // que la wallet estaba rota cuando estaba funcionando bien.
                if (!isLoading && !isBalanceHidden && (!isBalanceVerified || isBalanceIncomplete)) {
                    val esBloqueante = !isBalanceVerified
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (esBloqueante) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            Color(0xFFFF9800).copy(alpha = 0.12f)
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Warning,
                                contentDescription = "Incomplete balance",
                                tint = if (esBloqueante) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    Color(0xFFFF9800)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = balanceVerificationIssue ?: if (isBalanceIncomplete) {
                                    "$missingTxCount private transaction " +
                                        "${if (missingTxCount == 1) "body is" else "bodies are"} still missing. " +
                                        "This balance may be lower than the real balance and cannot be spent yet."
                                } else {
                                    "This saved balance has not been verified at the current P2P chain tip."
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (esBloqueante) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    textSecondary
                                }
                            )
                        }
                    }
                }

                if (!isLoading && hasMining && !isBalanceHidden) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFF9800).copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = "Maturing",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                "$immatureBalance ZKA maturing",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                ),
                                color = Color(0xFFFF9800)
                            )
                        }
                    }
                }

                // Stats live on the dedicated Charts screen (arrow icon at
                // the top-right of this card opens it). No inline price row
                // here — keeps the balance card focused on balance.
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Web3ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color,
    cardBackground: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )

    Card(
        onClick = onClick,
        modifier = modifier
            .height(80.dp)
            .scale(scale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            color.copy(alpha = 0.1f),
                            color.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = color.copy(alpha = 0.15f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    color = textColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color,
    cardBackground: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Unified monochrome scheme — black card with white icon + label so all
    // four quick actions read as one toolbar instead of four competing
    // accent colors. `color` is intentionally ignored; kept in the signature
    // for callsite compatibility.
    val containerColor = if (enabled) Color.Black else Color.Black.copy(alpha = 0.4f)
    val contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)

    // Press animation
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "cardScale"
    )

    Card(
        onClick = { if (enabled) onClick() },
        modifier = modifier
            .height(72.dp)
            .scale(scale)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            tryAwaitRelease()
                            isPressed = false
                        }
                    )
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                color = contentColor
            )
            if (!enabled) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun Web3TransactionItem(
    transaction: Transaction,
    walletAddress: String,
    accentColorReceive: Color,
    accentColorSent: Color,
    accentColorSuccess: Color,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Match the quick-actions toolbar: solid black card with white content.
    // `accentColorSent` / `accentColorReceive` / `accentColorSuccess` /
    // `cardBackground` are still received so the call-sites compile, but
    // they're intentionally ignored to keep the Recent Activity list
    // visually aligned with the Send/Receive/Accounts row above it.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Private txs aren't (and shouldn't be) on a public explorer,
                // so copy the hash for the user to verify on their own node
                // instead of opening a link that 404s and would leak the hash.
                if (transaction.hash.isNotBlank()) {
                    clipboard.setText(AnnotatedString(transaction.hash))
                    android.widget.Toast
                        .makeText(context, "Transaction hash copied", android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val isSent = transaction.from.equals(walletAddress, ignoreCase = true)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isSent) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (isSent) "Sent" else "Received",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        java.text.SimpleDateFormat("MMM dd, yyyy • HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(transaction.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    val amountValue = transaction.amount.toDoubleOrNull() ?: 0.0
                    Text(
                        "${if (isSent) "-" else "+"}${String.format("%.4f", amountValue)} ZKA",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = if (isSent) Color.White else Color(0xFF34C759)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val (statusColor, statusIcon, statusLabel) = when (transaction.status) {
                        TransactionStatus.PENDING ->
                            Triple(Color(0xFFF5A623), Icons.Outlined.Schedule, "Pending")
                        TransactionStatus.CONFIRMED ->
                            Triple(Color(0xFF34C759), Icons.Outlined.CheckCircle, "Confirmed")
                        TransactionStatus.FAILED ->
                            Triple(Color(0xFFFF3B30), Icons.Outlined.ErrorOutline, "Failed")
                    }
                    Surface(
                        color = statusColor.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                ),
                                color = statusColor
                            )
                        }
                    }
                }
            }

            // TX hash row — visible when pending so user can copy/verify
            if (transaction.status == TransactionStatus.PENDING && transaction.hash.isNotBlank()) {
                val clipboardMgr = LocalClipboardManager.current
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .clickable { clipboardMgr.setText(AnnotatedString(transaction.hash)) }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "TX",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = Color.White.copy(alpha = 0.4f)
                    )
                    Text(
                        "${transaction.hash.take(14)}…${transaction.hash.takeLast(10)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        ),
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy TX hash",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun Web3ShimmerTransaction(
    shimmerAlpha: Float,
    cardBackground: Color,
    shimmerColor: Color
) {
    // Shimmer placeholders on a black card so the empty loading row matches
    // the populated Web3TransactionItem above (also black).
    val placeholderColor = Color.White.copy(alpha = shimmerAlpha * 0.18f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(placeholderColor, CircleShape)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(16.dp)
                        .background(placeholderColor, RoundedCornerShape(4.dp))
                )
                Box(
                    modifier = Modifier
                        .width(150.dp)
                        .height(12.dp)
                        .background(placeholderColor, RoundedCornerShape(4.dp))
                )
            }
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(16.dp)
                    .background(placeholderColor, RoundedCornerShape(4.dp))
            )
        }
    }
}

@Composable
private fun Web3EmptyState(
    accentColor: Color,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Receipt,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Text(
                "No transactions yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                ),
                color = textPrimary
            )
            Text(
                "Your transaction history will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ModernNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val iconTint = if (isDark) Color.White else Color.Black
    val iconTintSecondary = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) iconTint else iconTintSecondary,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) iconTint else iconTintSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun BalanceCard(
    balance: String,
    usdValue: String,
    showUsdValue: Boolean,
    onToggleUsdDisplay: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Balance",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = "$balance ZKA",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.clickable { onToggleUsdDisplay() }
                )

                if (showUsdValue) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "≈ $${formatNumber(usdValue)} USD",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Price metrics row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PriceMetric(
                        label = "Price",
                        value = "$0.0342",
                        change = "+2.4%",
                        isPositive = true
                    )
                    
                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    )
                    
                    PriceMetric(
                        label = "24h Change",
                        value = "+0.0008",
                        change = "+2.4%",
                        isPositive = true
                    )
                    
                    Divider(
                        modifier = Modifier
                            .height(40.dp)
                            .width(1.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
                    )
                    
                    PriceMetric(
                        label = "Market Cap",
                        value = "$34.2M",
                        change = "Rank #450",
                        isPositive = null
                    )
                }
            }
        }
    }
}

@Composable
fun PriceMetric(
    label: String,
    value: String,
    change: String,
    isPositive: Boolean?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = change,
            style = MaterialTheme.typography.bodySmall,
            color = when (isPositive) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
            }
        )
    }
}

@Composable
fun TransactionItem(transaction: Transaction, activeWallet: String?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transaction icon with background
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when {
                    transaction.from == (activeWallet ?: "") -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Icon(
                    imageVector = when {
                        transaction.from == (activeWallet ?: "") -> Icons.Default.ArrowUpward
                        else -> Icons.Default.ArrowDownward
                    },
                    contentDescription = null,
                    tint = when {
                        transaction.from == (activeWallet ?: "") -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Transaction details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        transaction.from == (activeWallet ?: "") -> "Sent to ${transaction.to.take(8)}..."
                        else -> "Received from ${transaction.from.take(8)}..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${transaction.amount} ${transaction.token.symbol}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        transaction.from == (activeWallet ?: "") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            // Status indicator
            if (transaction.status == TransactionStatus.PENDING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (transaction.status == TransactionStatus.CONFIRMED) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Confirmed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ErrorDialog(
    error: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Error") },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Retry")
            }
        }
    )
}

private fun formatNumber(value: String): String {
    return try {
        NumberFormat.getInstance().format(value.toBigDecimal())
    } catch (e: Exception) {
        value
    }
}

@Composable
fun ModernBalanceCard(
    balance: String,
    usdValue: String,
    isLoading: Boolean,
    shimmerAlpha: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Total Balance",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLoading) {
                    Surface(
                        modifier = Modifier
                            .width(150.dp)
                            .height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.2f)
                    ) {}
                } else {
                    Text(
                        "$balance ZKA",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (isLoading) {
                    Surface(
                        modifier = Modifier
                            .width(100.dp)
                            .height(20.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = shimmerAlpha * 0.2f)
                    ) {}
                } else {
                    Text(
                        "≈ $$usdValue USD",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ModernPriceMetricsCard(priceInfo: Any?) {
    // TODO: Implement when priceInfo is added to DashboardState
    /*
    if (priceInfo == null) return
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PriceMetricItem(
                icon = Icons.Outlined.AttachMoney,
                label = "Price",
                value = "$${String.format("%.2f", priceInfo.price)}",
                modifier = Modifier.weight(1f)
            )
            
            PriceMetricItem(
                icon = Icons.Outlined.TrendingUp,
                label = "24h Change",
                value = "${String.format("%.2f", priceInfo.change24h)}%",
                valueColor = if (priceInfo.change24h >= 0) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            
            PriceMetricItem(
                icon = Icons.Outlined.ShowChart,
                label = "Market Cap",
                value = formatMarketCap(priceInfo.marketCap),
                modifier = Modifier.weight(1f)
            )
        }
    }
    */
}

@Composable
fun PriceMetricItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ),
            color = valueColor
        )
    }
}

@Composable
fun ModernTransactionItem(
    transaction: Transaction,
    walletAddress: String
) {
    // Determine if outgoing based on the current wallet address
    val isOutgoing = transaction.from.equals(walletAddress, ignoreCase = true)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = if (isOutgoing) 
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                else 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isOutgoing) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                        contentDescription = null,
                        tint = if (isOutgoing) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isOutgoing) "Sent" else "Received",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    java.text.SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
                        .format(java.util.Date(transaction.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${if (isOutgoing) "-" else "+"}${transaction.amount} ${transaction.token.symbol}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = if (isOutgoing) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                StatusBadge(status = transaction.status)
            }
        }
    }
}

@Composable
fun StatusBadge(status: TransactionStatus) {
    val (text, color) = when (status) {
        TransactionStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.tertiary
        TransactionStatus.CONFIRMED -> "Confirmed" to MaterialTheme.colorScheme.primary
        TransactionStatus.FAILED -> "Failed" to MaterialTheme.colorScheme.error
    }
    
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
fun ShimmerTransactionItem() {
    val shimmerTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by shimmerTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
            ) {}
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Surface(
                    modifier = Modifier
                        .width(100.dp)
                        .height(20.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
                ) {}
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .width(150.dp)
                        .height(16.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.2f)
                ) {}
            }
        }
    }
}

@Composable
fun EmptyTransactionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.ReceiptLong,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Transactions Yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your transaction history will appear here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatMarketCap(value: Double): String {
    return when {
        value >= 1_000_000_000 -> String.format("$%.2fB", value / 1_000_000_000)
        value >= 1_000_000 -> String.format("$%.2fM", value / 1_000_000)
        value >= 1_000 -> String.format("$%.2fK", value / 1_000)
        else -> String.format("$%.2f", value)
    }
}

@Composable
private fun FlatNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isDarkTheme) Color.White else Color.Black,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) 
                    androidx.compose.ui.text.font.FontWeight.SemiBold 
                else 
                    androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color = if (isDarkTheme) Color.White else Color.Black,
            maxLines = 1,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ModernNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    onClick: () -> Unit
) {
    // Consistent colors - black container with white icon when selected (light mode)
    val selectedBg = if (isDarkTheme) Color.White else Color.Black
    val selectedContent = if (isDarkTheme) Color.Black else Color.White
    val unselectedColor = if (isDarkTheme) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
    
    // Animated scale on selection
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "navScale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
            .background(if (isSelected) selectedBg else Color.Transparent)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) selectedContent else unselectedColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) 
                    androidx.compose.ui.text.font.FontWeight.Bold 
                else 
                    androidx.compose.ui.text.font.FontWeight.Normal
            ),
            color = if (isSelected) selectedContent else unselectedColor,
            maxLines = 1,
            fontSize = 10.sp
        )
    }
}
