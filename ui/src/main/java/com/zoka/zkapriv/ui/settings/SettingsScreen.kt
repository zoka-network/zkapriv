package com.zoka.zkapriv.ui.settings

import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onShowMnemonic: () -> Unit,
    onResetWallet: () -> Unit = {},
    onManageAccounts: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val appVersionLabel = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()?.let { "v$it" } ?: ""
    }

    var showSeedPhraseDialog by remember { mutableStateOf(false) }
    var showScanKeyDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showPinResultDialog by remember { mutableStateOf(false) }
    var showClearDataDialog by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Appearance Section
            SettingsSection(title = "Appearance") {
                var showThemeDialog by remember { mutableStateOf(false) }
                
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Palette,
                        title = "Theme",
                        subtitle = when (uiState.themeMode) {
                            com.zoka.zkapriv.core.preferences.ThemeMode.LIGHT -> "Light Mode"
                            com.zoka.zkapriv.core.preferences.ThemeMode.DARK -> "Dark Mode"
                            com.zoka.zkapriv.core.preferences.ThemeMode.SYSTEM -> "System Default"
                        },
                        onClick = { showThemeDialog = true }
                    )
                }
                
                if (showThemeDialog) {
                    ModernThemeDialog(
                        currentTheme = uiState.themeMode,
                        onDismiss = { showThemeDialog = false },
                        onThemeSelected = { theme ->
                            viewModel.setThemeMode(theme)
                            showThemeDialog = false
                        }
                    )
                }
            }
            
            // Security Section
            SettingsSection(title = "Security") {
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Lock,
                        title = "Change PIN",
                        subtitle = "Update your security PIN",
                        onClick = { showChangePinDialog = true }
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Biometric Login",
                        subtitle = "Use fingerprint or face ID",
                        trailing = {
                            Switch(
                                checked = uiState.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        viewModel.enableBiometric(context as FragmentActivity) { error ->
                                            biometricError = error
                                        }
                                    } else {
                                        viewModel.disableBiometric()
                                    }
                                }
                            )
                        }
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.Visibility,
                        title = "Show Recovery Phrase",
                        subtitle = "View your 12-word seed phrase",
                        onClick = { showSeedPhraseDialog = true }
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.Key,
                        title = "Show Scan Key",
                        subtitle = "View your scan key",
                        onClick = { showScanKeyDialog = true }
                    )
                }
            }

            // Wallet Section with modern card
            SettingsSection(title = "Wallet") {
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = uiState.activeAccountName,
                        subtitle = uiState.activeWallet?.let { 
                            "${it.take(12)}...${it.takeLast(8)}"
                        } ?: "No wallet selected",
                        showArrow = false
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.ManageAccounts,
                        title = "Manage Accounts",
                        subtitle = "Create, switch, or remove accounts",
                        onClick = onManageAccounts
                    )
                }
            }

            // Network Section — custom RPC endpoint for accurate balance scanning
            SettingsSection(title = "Network") {
                var showRpcDialog by remember { mutableStateOf(false) }
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Cloud,
                        title = "RPC endpoint",
                        subtitle = uiState.rpcEndpoint.trimEnd('/'),
                        onClick = { showRpcDialog = true }
                    )
                }
                if (showRpcDialog) {
                    RpcEndpointDialog(
                        currentEndpoint = uiState.rpcEndpoint,
                        isCustom = uiState.isCustomRpc,
                        onSave = { url ->
                            viewModel.setRpcEndpoint(url)
                            showRpcDialog = false
                        },
                        onReset = {
                            viewModel.resetRpcEndpoint()
                            showRpcDialog = false
                        },
                        onDismiss = { showRpcDialog = false }
                    )
                }
            }

            // Transaction History Backup Section
            SettingsSection(title = "Transaction History") {
                var showExportSuccess by remember { mutableStateOf(false) }
                var showImportDialog by remember { mutableStateOf(false) }
                var exportedFilePath by remember { mutableStateOf("") }
                
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Upload,
                        title = "Export History",
                        subtitle = "Save transaction history to file",
                        onClick = {
                            viewModel.exportTransactionHistory { filePath ->
                                exportedFilePath = filePath
                                showExportSuccess = true
                            }
                        }
                    )
                    
                    Divider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    
                    ModernSettingsItem(
                        icon = Icons.Outlined.Download,
                        title = "Import History",
                        subtitle = "Restore transaction history from backup",
                        onClick = { showImportDialog = true }
                    )
                }
                
                // Export Success Dialog
                if (showExportSuccess) {
                    AlertDialog(
                        onDismissRequest = { showExportSuccess = false },
                        icon = { 
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(48.dp)
                            ) 
                        },
                        title = { 
                            Text(
                                "✓ Export Successful",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            ) 
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Your transaction history has been exported successfully.")
                                
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            "File saved to:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            exportedFilePath,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = { showExportSuccess = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text("OK")
                            }
                        }
                    )
                }
                
                // Import Dialog - List available backup files
                if (showImportDialog) {
                    var availableBackups by remember { mutableStateOf<List<java.io.File>>(emptyList()) }
                    var importSuccess by remember { mutableStateOf(false) }
                    var importedCount by remember { mutableStateOf(0) }
                    var importError by remember { mutableStateOf<String?>(null) }
                    
                    // Load available backup files
                    LaunchedEffect(showImportDialog) {
                        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                        val backupFiles = downloadsDir?.listFiles { file ->
                            file.name.startsWith("zkapriv_backup_") && file.name.endsWith(".json")
                        }?.sortedByDescending { it.lastModified() } ?: emptyList()
                        availableBackups = backupFiles
                    }
                    
                    // Listen for errors from ViewModel
                    LaunchedEffect(uiState.error) {
                        if (uiState.error != null) {
                            importError = uiState.error
                            viewModel.clearError()
                        }
                    }
                    
                    AlertDialog(
                        onDismissRequest = { 
                            showImportDialog = false
                            importSuccess = false
                            importError = null
                        },
                        icon = { 
                            Icon(
                                if (importSuccess) Icons.Default.CheckCircle else Icons.Outlined.Download,
                                contentDescription = null,
                                tint = if (importSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            ) 
                        },
                        title = { 
                            Text(
                                if (importSuccess) "✓ Import Successful" else "Import History",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            ) 
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (importSuccess) {
                                    Text("Successfully imported $importedCount new transactions to your history.")
                                } else if (importError != null) {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                "Error",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.error
                                            )
                                            Text(
                                                importError ?: "",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                } else {
                                    if (availableBackups.isEmpty()) {
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.FolderOpen,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "No backup files found",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    "Export your transaction history first",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    } else {
                                        Text(
                                            "Select a backup file to restore:",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                        )
                                        
                                        availableBackups.forEach { file ->
                                            Card(
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                                onClick = {
                                                    viewModel.importTransactionHistory(file.absolutePath) { count ->
                                                        importedCount = count
                                                        importSuccess = true
                                                    }
                                                }
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(12.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            file.name,
                                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                            ),
                                                            maxLines = 1
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        Text(
                                                            java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.US)
                                                                .format(java.util.Date(file.lastModified())),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            if (importSuccess || importError != null) {
                                Button(
                                    onClick = { 
                                        showImportDialog = false
                                        importSuccess = false
                                        importError = null
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (importSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("OK")
                                }
                            }
                        },
                        dismissButton = {
                            if (!importSuccess && importError == null) {
                                TextButton(onClick = { showImportDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    )
                }
            }

            // About Section - Collapsible
            SettingsSection(title = "About") {
                var showAboutDialog by remember { mutableStateOf(false) }
                
                ModernSettingsCard {
                    ModernSettingsItem(
                        icon = Icons.Outlined.Info,
                        title = "zkapriv",
                        subtitle = "Version 0.1.0 - Tap to view details",
                        onClick = { showAboutDialog = true }
                    )
                }
                
                if (showAboutDialog) {
                    Dialog(
                        onDismissRequest = { showAboutDialog = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .wrapContentHeight(),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                // Header con gradiente
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF1a1a2e),
                                                    Color(0xFF16213e)
                                                )
                                            )
                                        )
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Logo
                                        androidx.compose.foundation.Image(
                                            painter = androidx.compose.ui.res.painterResource(com.zoka.zkapriv.ui.R.drawable.zkapriv_logo),
                                            contentDescription = "Zoka Logo",
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                        )

                                        Text(
                                            text = "zkapriv",
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = Color.White
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = Color.White.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                text = appVersionLabel,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                                style = MaterialTheme.typography.labelLarge,
                                                color = Color.White
                                            )
                                        }

                                        Text(
                                            text = "ZK Privacy Wallet · Zoka Network",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                
                                // Contenido
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Descripción
                                    Text(
                                        text = "A self-custodial ZK privacy wallet for the Zoka blockchain. Your keys, your coins — protected by zero-knowledge cryptography.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )

                                    // Stats Row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        AboutFeatureItem(icon = Icons.Outlined.Lock, label = "Non-Custodial")
                                        AboutFeatureItem(icon = Icons.Outlined.Shield, label = "ZK Privacy")
                                        AboutFeatureItem(icon = Icons.Outlined.Code, label = "Open Source")
                                    }

                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Technology Section
                                    Text(
                                        text = "Technology",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        TechChip("Kotlin")
                                        TechChip("Jetpack Compose")
                                        TechChip("Ristretto255")
                                        TechChip("BIP-39")
                                        TechChip("ZK Proofs")
                                    }

                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                                    // Links Section
                                    Text(
                                        text = "Links",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(com.zoka.zkapriv.core.util.Constants.ZOKA_WEBSITE_URL)
                                                )
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Website")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(com.zoka.zkapriv.core.util.Constants.ZOKA_EXPLORER_URL)
                                                )
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Explorer")
                                        }
                                    }
                                    
                                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    
                                    // Developer
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Code,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Developer",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Natosh Zoka",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.SemiBold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Outlined.Verified,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    // Social Links
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { 
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("https://github.com/zoka-network/zkapriv")
                                                )
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Code,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("GitHub")
                                        }
                                        OutlinedButton(
                                            onClick = { 
                                                val intent = android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse("https://x.com/ZokaNetwork")
                                                )
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("𝕏", fontWeight = FontWeight.Bold)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Twitter")
                                        }
                                    }
                                    
                                    // Close Button
                                    Button(
                                        onClick = { showAboutDialog = false },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF1a1a2e),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text(
                                            text = "Close",
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Danger Zone with modern card
            SettingsSection(
                title = "Danger Zone",
                titleColor = MaterialTheme.colorScheme.error
            ) {
                // Detect theme for proper styling
                val bgColor = MaterialTheme.colorScheme.background
                val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = if (isDarkTheme) 0.dp else 4.dp
                    )
                ) {
                    ModernSettingsItem(
                        icon = Icons.Outlined.DeleteForever,
                        title = "Reset Wallet",
                        subtitle = "Remove local wallet data from this device",
                        onClick = { showClearDataDialog = true },
                        textColor = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Change PIN Dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { 
                showChangePinDialog = false
                viewModel.clearPinChangeStatus()
            },
            onConfirm = { oldPin, newPin ->
                viewModel.changePin(oldPin, newPin)
                showChangePinDialog = false
                showPinResultDialog = true
            }
        )
    }
    
    // PIN Change Result Dialog
    if (showPinResultDialog) {
        AlertDialog(
            onDismissRequest = { 
                showPinResultDialog = false
                viewModel.clearPinChangeStatus()
            },
            icon = {
                Icon(
                    imageVector = if (uiState.pinChangeSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (uiState.pinChangeSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = if (uiState.pinChangeSuccess) "✓ PIN Changed Successfully" else "✗ PIN Change Failed",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = if (uiState.pinChangeSuccess) {
                        "Your PIN has been updated successfully. Please use the new PIN for future authentication."
                    } else {
                        uiState.error ?: "Failed to change PIN. Please try again."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPinResultDialog = false
                        viewModel.clearPinChangeStatus()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.pinChangeSuccess) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("OK")
                }
            }
        )
    }

    // Clear Data Confirmation with Strong Warning
    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            icon = { 
                Icon(
                    Icons.Default.Warning, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                ) 
            },
            title = { 
                Text(
                    "⚠️ Delete Everything?",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.error
                ) 
            },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "This action cannot be undone!",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "You are about to permanently delete:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("• All wallet addresses", style = MaterialTheme.typography.bodyMedium)
                        Text("• Your recovery phrase", style = MaterialTheme.typography.bodyMedium)
                        Text("• Transaction history", style = MaterialTheme.typography.bodyMedium)
                        Text("• All app settings", style = MaterialTheme.typography.bodyMedium)
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "⚠️ Make sure you have backed up your recovery phrase before proceeding. Without it, you will lose access to your funds forever!",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData {
                            showClearDataDialog = false
                            onResetWallet()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Yes, Delete Everything")
                }
            },
            dismissButton = {
                FilledTonalButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    // Biometric Error Dialog
    if (biometricError != null) {
        AlertDialog(
            onDismissRequest = { biometricError = null },
            icon = { 
                Icon(
                    Icons.Default.Error, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                ) 
            },
            title = { Text("Biometric Error") },
            text = { Text(biometricError ?: "") },
            confirmButton = {
                TextButton(onClick = { biometricError = null }) {
                    Text("OK")
                }
            }
        )
    }
    
    // Seed Phrase Dialog with Authentication
    if (showSeedPhraseDialog) {
        SeedPhraseRevealDialog(
            seedPhrase = uiState.seedPhrase ?: "",
            onDismiss = { showSeedPhraseDialog = false },
            viewModel = viewModel
        )
    }
    
    // Scan Key Dialog with Authentication
    if (showScanKeyDialog) {
        ScanKeyRevealDialog(
            onDismiss = { showScanKeyDialog = false },
            viewModel = viewModel
        )
    }

    // Error handling
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun RpcEndpointDialog(
    currentEndpoint: String,
    isCustom: Boolean,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf(currentEndpoint.trimEnd('/')) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(40.dp))
        },
        title = { Text("Custom RPC endpoint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Point ZKAPriv to your own Zoka full node for an exact balance (the public RPC may prune old private txs).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RPC URL") },
                    placeholder = { Text("http://192.168.x.x:3000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Tip: when phone is connected by USB, run on PC: adb reverse tcp:3000 tcp:3000 — then use http://127.0.0.1:3000",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(url) }) { Text("Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isCustom) {
                    TextButton(onClick = onReset) { Text("Use default") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun SettingsSection(
    title: String,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onBackground,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = titleColor,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        content()
    }
}

@Composable
fun ModernSettingsCard(
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable ColumnScope.() -> Unit
) {
    // Detect if dark theme based on background color
    val bgColor = MaterialTheme.colorScheme.background
    val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDarkTheme) 0.dp else 4.dp
        )
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun ModernSettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    showArrow: Boolean = true
) {
    // Detect if dark theme based on background color
    val bgColor = MaterialTheme.colorScheme.background
    val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
    val iconContainerColor = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Black
    val iconTintColor = Color.White
    
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with gradient background
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = iconContainerColor
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTintColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    ),
                    color = textColor
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.6f)
                    )
                }
            }

            if (trailing != null) {
                trailing()
            } else if (showArrow && onClick != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun ModernThemeDialog(
    currentTheme: com.zoka.zkapriv.core.preferences.ThemeMode,
    onDismiss: () -> Unit,
    onThemeSelected: (com.zoka.zkapriv.core.preferences.ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { 
            Icon(
                Icons.Outlined.Palette,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            ) 
        },
        title = { 
            Text(
                "Select Theme",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            ) 
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeOption(
                    icon = Icons.Outlined.LightMode,
                    title = "Light Mode",
                    subtitle = "Always use light theme",
                    selected = currentTheme == com.zoka.zkapriv.core.preferences.ThemeMode.LIGHT,
                    onClick = { onThemeSelected(com.zoka.zkapriv.core.preferences.ThemeMode.LIGHT) }
                )
                
                ThemeOption(
                    icon = Icons.Outlined.DarkMode,
                    title = "Dark Mode",
                    subtitle = "Always use dark theme",
                    selected = currentTheme == com.zoka.zkapriv.core.preferences.ThemeMode.DARK,
                    onClick = { onThemeSelected(com.zoka.zkapriv.core.preferences.ThemeMode.DARK) }
                )
                
                ThemeOption(
                    icon = Icons.Outlined.Brightness4,
                    title = "System Default",
                    subtitle = "Follow system settings",
                    selected = currentTheme == com.zoka.zkapriv.core.preferences.ThemeMode.SYSTEM,
                    onClick = { onThemeSelected(com.zoka.zkapriv.core.preferences.ThemeMode.SYSTEM) }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun ThemeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) 
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else 
            androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = if (selected)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) 
                            androidx.compose.ui.text.font.FontWeight.SemiBold 
                        else 
                            androidx.compose.ui.text.font.FontWeight.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            RadioButton(
                selected = selected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    textColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        onClick = { onClick?.invoke() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }

            trailing?.invoke()
        }
    }
}

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { if (it.length <= 6) oldPin = it },
                    label = { Text("Current PIN") },
                    singleLine = true,
                    visualTransformation = com.zoka.zkapriv.ui.onboarding.screens.PinVisualTransformation()
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { if (it.length <= 6) newPin = it },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = com.zoka.zkapriv.ui.onboarding.screens.PinVisualTransformation()
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6) confirmPin = it },
                    label = { Text("Confirm New PIN") },
                    singleLine = true,
                    visualTransformation = com.zoka.zkapriv.ui.onboarding.screens.PinVisualTransformation(),
                    isError = error != null
                )

                if (error != null) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        oldPin.length != 6 || newPin.length != 6 || confirmPin.length != 6 -> {
                            error = "PIN must be 6 digits"
                        }
                        newPin != confirmPin -> {
                            error = "New PINs do not match"
                        }
                        else -> {
                            onConfirm(oldPin, newPin)
                        }
                    }
                }
            ) {
                Text("Change PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SeedPhraseRevealDialog(
    seedPhrase: String,
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var useBiometric by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val uiState by viewModel.uiState.collectAsState()
    
    // Use seed phrase or scan key from uiState instead of parameter
    val currentSeedPhrase = uiState.seedPhrase ?: ""
    val currentScanKey = uiState.scanKeyHex ?: ""
    val isScanKeyImport = uiState.isScanKeyImport
    
    // Setup biometric prompt for seed phrase reveal
    val seedBiometricPrompt = remember {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isAuthenticated = true
                        error = null
                        viewModel.loadSeedPhrase()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        error = "Biometric authentication failed: $errString"
                        useBiometric = false
                    }

                    override fun onAuthenticationFailed() {
                        error = "Biometric authentication failed"
                    }
                }
            )
        }
    }

    val seedPromptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Authenticate to view recovery phrase")
            .setNegativeButtonText("Use PIN")
            .build()
    }
    
    // Show biometric prompt when dialog opens if biometric is enabled
    LaunchedEffect(Unit) {
        if (uiState.biometricEnabled) {
            val biometricManager = AndroidBiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(
                AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG or 
                AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            
            if (canAuthenticate == AndroidBiometricManager.BIOMETRIC_SUCCESS) {
                useBiometric = true
                seedBiometricPrompt?.authenticate(seedPromptInfo)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Shield,
                contentDescription = null,
                tint = if (isAuthenticated) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = if (isAuthenticated) {
                    if (isScanKeyImport) "Scan Key" else "Recovery Phrase"
                } else {
                    "Verify Identity"
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
        },
        text = {
            if (!isAuthenticated) {
                // Authentication screen
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (useBiometric) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Text(
                                "Touch the fingerprint sensor",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            TextButton(onClick = { useBiometric = false }) {
                                Text("Use PIN instead")
                            }
                        }
                    } else {
                        Text(
                            "Enter your PIN to view your recovery phrase",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                            label = { Text("PIN") },
                            singleLine = true,
                            visualTransformation = com.zoka.zkapriv.ui.onboarding.screens.PinVisualTransformation(),
                            isError = error != null,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        // Option to use biometric if available and enabled
                        if (uiState.biometricEnabled) {
                            TextButton(
                                onClick = { 
                                    useBiometric = true
                                    seedBiometricPrompt?.authenticate(seedPromptInfo)
                                }
                            ) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Use Biometric")
                            }
                        }
                    }
                    
                    if (error != null) {
                        Text(
                            text = error ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                // Show seed phrase
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "Warning",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Text(
                                if (isScanKeyImport) {
                                    "Never share your scan key with anyone. Anyone with this key can access your wallet."
                                } else {
                                    "Never share your recovery phrase with anyone. Anyone with this phrase can access your wallet."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isScanKeyImport && currentScanKey.isNotEmpty()) {
                                // Display scan key
                                Text(
                                    "Scan Key",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        currentScanKey,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                        ),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            } else if (currentSeedPhrase.isNotEmpty()) {
                                // Display mnemonic seed phrase
                                val words = currentSeedPhrase.trim().split(Regex("\\s+"))
                                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.height(((words.size + 1) / 2 * 44).dp)
                                ) {
                                    items(words.size) { index ->
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                horizontalArrangement = Arrangement.Start,
                                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "${index + 1}.",
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                    ),
                                                    modifier = Modifier.padding(end = 4.dp)
                                                )
                                                Text(
                                                    words[index],
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    "No recovery information found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isAuthenticated && !useBiometric) {
                Button(
                    onClick = {
                        if (pin.length == 6) {
                            if (viewModel.verifyPin(pin)) {
                                isAuthenticated = true
                                error = null
                                viewModel.loadSeedPhrase()
                            } else {
                                error = "Incorrect PIN"
                            }
                        } else {
                            error = "PIN must be 6 digits"
                        }
                    },
                    enabled = pin.length == 6
                ) {
                    Text("Verify")
                }
            } else if (isAuthenticated) {
                var showCopied by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current
                val contentToCopy = if (isScanKeyImport) currentScanKey else currentSeedPhrase
                
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val label = if (isScanKeyImport) "Scan Key" else "Recovery Phrase"
                        val clip = android.content.ClipData.newPlainText(label, contentToCopy)
                        clipboard.setPrimaryClip(clip)
                        showCopied = true
                    },
                    enabled = contentToCopy.isNotEmpty()
                ) {
                    Icon(
                        imageVector = if (showCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (showCopied) "Copied!" else "Copy")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ScanKeyRevealDialog(
    onDismiss: () -> Unit,
    viewModel: SettingsViewModel
) {
    var isAuthenticated by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var useBiometric by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    
    // Use scan key and zpriv data from uiState
    val scanKeyHex = uiState.scanKeyHex ?: ""
    val zpriv = uiState.zpriv ?: ""
    
    val privKeyBiometricPrompt = remember {
        activity?.let {
            BiometricPrompt(
                it,
                ContextCompat.getMainExecutor(context),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        isAuthenticated = true
                        error = null
                        viewModel.loadScanKey()
                    }
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        error = "Biometric authentication failed: $errString"
                        useBiometric = false
                    }
                    override fun onAuthenticationFailed() {
                        error = "Biometric authentication failed"
                    }
                }
            )
        }
    }

    val privKeyPromptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verify Identity")
            .setSubtitle("Authenticate to view scan key")
            .setNegativeButtonText("Use PIN")
            .build()
    }
    
    LaunchedEffect(Unit) {
        if (uiState.biometricEnabled) {
            val biometricManager = AndroidBiometricManager.from(context)
            val canAuthenticate = biometricManager.canAuthenticate(
                AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG or 
                AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            
            if (canAuthenticate == AndroidBiometricManager.BIOMETRIC_SUCCESS) {
                useBiometric = true
                privKeyBiometricPrompt?.authenticate(privKeyPromptInfo)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = if (isAuthenticated) androidx.compose.ui.graphics.Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = if (isAuthenticated) "Export Keys" else "Verify Identity",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
        },
        text = {
            if (!isAuthenticated) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (useBiometric) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Text("Touch the fingerprint sensor")
                            TextButton(onClick = { useBiometric = false }) {
                                Text("Use PIN instead")
                            }
                        }
                    } else {
                        Text("Enter your PIN to view your scan key")
                        OutlinedTextField(
                            value = pin,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                            label = { Text("PIN") },
                            singleLine = true,
                            visualTransformation = com.zoka.zkapriv.ui.onboarding.screens.PinVisualTransformation(),
                            isError = error != null,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.biometricEnabled) {
                            TextButton(onClick = { 
                                useBiometric = true
                                privKeyBiometricPrompt?.authenticate(privKeyPromptInfo)
                            }) {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Use Biometric")
                            }
                        }
                    }
                    if (error != null) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                Text("Warning", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            }
                            Text("Never share your scan key. Anyone with this key can control your wallet. The zpriv receive material is safe to share for verification.")
                        }
                    }
                    
                    // Scan Key
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "🔐 Scan Key", 
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ), 
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    "DO NOT SHARE", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = scanKeyHex,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // zpriv receive material
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "🔓 zpriv Address Material", 
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    ), 
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "SAFE TO SHARE", 
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = zpriv,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isAuthenticated) {
                var showCopiedScanKey by remember { mutableStateOf(false) }
                var showCopiedZpriv by remember { mutableStateOf(false) }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Copy Scan Key
                    Button(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(scanKeyHex))
                            showCopiedScanKey = true
                            showCopiedZpriv = false
                        },
                        enabled = scanKeyHex.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            if (showCopiedScanKey) Icons.Default.Check else Icons.Default.ContentCopy, 
                            contentDescription = null, 
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showCopiedScanKey) "Scan key copied" else "Scan key")
                    }
                    
                    // Copy zpriv Receive Material
                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(zpriv))
                            showCopiedZpriv = true
                            showCopiedScanKey = false
                        },
                        enabled = zpriv.isNotEmpty()
                    ) {
                        Icon(
                            if (showCopiedZpriv) Icons.Default.Check else Icons.Default.ContentCopy, 
                            contentDescription = null, 
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(if (showCopiedZpriv) "zpriv copied" else "zpriv")
                    }
                }
            } else if (!useBiometric) {
                Button(
                    onClick = {
                        if (viewModel.verifyPin(pin)) {
                            isAuthenticated = true
                            error = null
                            viewModel.loadScanKey()
                        } else {
                            error = "Incorrect PIN"
                        }
                    },
                    enabled = pin.length == 6
                ) {
                    Text("Verify")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
// About Dialog Components
@Composable
private fun AboutFeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    // Same style as ModernSettingsItem icons
    val bgColor = MaterialTheme.colorScheme.background
    val isDarkTheme = (bgColor.red * 0.299f + bgColor.green * 0.587f + bgColor.blue * 0.114f) < 0.5f
    val iconContainerColor = if (isDarkTheme) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Black
    val iconTintColor = Color.White
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(14.dp),
            color = iconContainerColor
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTintColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AboutStatItem(icon: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            fontSize = 24.sp
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TechChip(text: String) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
