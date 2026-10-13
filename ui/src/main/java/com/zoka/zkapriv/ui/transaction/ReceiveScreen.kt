package com.zoka.zkapriv.ui.transaction

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    onClose: () -> Unit,
    viewModel: ReceiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    var showCopiedSnackbar by remember { mutableStateOf(false) }
    
    // Animation for QR code appearance
    val qrScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "qrScale"
    )

    LaunchedEffect(showCopiedSnackbar) {
        if (showCopiedSnackbar) {
            kotlinx.coroutines.delay(2000)
            showCopiedSnackbar = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Receive ZKA",
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
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = {
            AnimatedVisibility(
                visible = showCopiedSnackbar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Address copied to clipboard")
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        // Detect theme for proper styling
        val bgColor = MaterialTheme.colorScheme.background
        val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
        val iconContainerColor = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Black
        val iconTintColor = androidx.compose.ui.graphics.Color.White
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // QR Code Card with animation
            Card(
                modifier = Modifier
                    .size(280.dp)
                    .scale(qrScale),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    QRCode(
                        content = uiState.qrContent,
                        size = 220
                    )
                }
            }

            // Address Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDarkTheme) 0.dp else 4.dp
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = iconContainerColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = iconTintColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Your Address",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                        )
                    }
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = uiState.address,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                ),
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            FilledIconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(uiState.address))
                                    showCopiedSnackbar = true
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    "Copy address",
                                    modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            uiState.hybridAddress?.let { zkq1 ->
                HybridAddressCard(
                    address = zkq1,
                    isDarkTheme = isDarkTheme,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(zkq1))
                        showCopiedSnackbar = true
                    }
                )
            }

            // Extra space at bottom for scrolling
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}            // Optional Amount Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isDarkTheme) 0.dp else 4.dp
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = iconContainerColor
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Outlined.RequestQuote,
                                    contentDescription = null,
                                    tint = iconTintColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Request Amount",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                )
                            )
                            Text(
                                "Optional - Leave empty for any amount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    // Amount Input Field
                    OutlinedTextField(
                        value = if (uiState.isUsdMode) uiState.usdAmount else uiState.requestAmount,
                        onValueChange = { viewModel.updateRequestAmount(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("0.00") },
                        label = { Text(if (uiState.isUsdMode) "Amount (USD)" else "Amount (ZKA)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        ),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.requestAmount.isNotEmpty() || uiState.usdAmount.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.clearAmount() }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear amount",
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                }
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
                        }
                    )
                    
                    // Show conversion equivalent
                    AnimatedVisibility(
                        visible = (uiState.isUsdMode && uiState.usdAmount.isNotEmpty()) || 
                                  (!uiState.isUsdMode && uiState.requestAmount.isNotEmpty()),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (uiState.isUsdMode) "≈ ${uiState.requestAmount} ZKA" else "≈ $${uiState.usdAmount} USD",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = uiState.error != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = uiState.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dirección zkq1 (post-cuántica). Solo existe en pantalla cuando la red activó
 * las salidas v2. Son unos 1.715 caracteres: se muestra abreviada, se copia
 * entera y el QR se genera con más resolución y corrección baja para que siga
 * siendo legible.
 */
@Composable
private fun HybridAddressCard(
    address: String,
    isDarkTheme: Boolean,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDarkTheme) 0.dp else 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Quantic address (zkq1)",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Payments to this address stay private against a future quantum computer. " +
                    "Wallets that do not support zkq1 yet can still pay your regular address.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = androidx.compose.ui.graphics.Color.White
            ) {
                Box(modifier = Modifier.padding(12.dp)) {
                    QRCode(content = address, size = 260, pixels = 1040, lowCorrection = true)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = address.take(20) + "…" + address.takeLast(12),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onCopy) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy zkq1 address")
            }
        }
    }
}

/**
 * [pixels] separa la resolución del bitmap del tamaño en pantalla: una zkq1
 * necesita un QR de unos 150 módulos por lado, ilegible a 220 píxeles.
 */
@Composable
fun QRCode(
    content: String,
    size: Int,
    pixels: Int = size,
    lowCorrection: Boolean = false
) {
    val qrCodeBitmap = remember(content, pixels, lowCorrection) {
        generateQRCode(content, pixels, lowCorrection)
    }

    if (qrCodeBitmap != null) {
        Image(
            bitmap = qrCodeBitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier.size(size.dp)
        )
    }
}

private fun generateQRCode(content: String, size: Int, lowCorrection: Boolean = false): Bitmap? {
    return try {
        val hints = mutableMapOf<com.google.zxing.EncodeHintType, Any>()
        hints[com.google.zxing.EncodeHintType.MARGIN] = 1
        if (lowCorrection) {
            hints[com.google.zxing.EncodeHintType.ERROR_CORRECTION] =
                com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.L
        }
        
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(
            content,
            BarcodeFormat.QR_CODE,
            size,
            size,
            hints
        )
        
        // Un solo setPixels: con el QR de la zkq1 (1040 x 1040) pintar pixel por
        // pixel tardaba lo suficiente como para notarse al abrir la pantalla.
        val pixelsArray = IntArray(size * size)
        for (y in 0 until size) {
            val fila = y * size
            for (x in 0 until size) {
                pixelsArray[fila + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixelsArray, 0, size, 0, 0, size, size)
        bitmap
    } catch (e: Exception) {
        null
    }
}