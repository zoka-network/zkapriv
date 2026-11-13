package com.zoka.zkapriv.ui.quantic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Animation
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.network.quantic.ViewKeyScope
import com.zoka.zkapriv.ui.components.CopyButton
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.MonoText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Segmented
import com.zoka.zkapriv.ui.components.ZkTone
import kotlinx.coroutines.CancellationException

@Composable
fun SettingsScreen(
    vm: WalletViewModel,
    onBack: (() -> Unit)?,
    onReveal: (String) -> Unit,
    onChangePin: () -> Unit,
    onReset: () -> Unit,
    onNetwork: () -> Unit = {},
    onWallets: () -> Unit = {},
) {
    val context = LocalContext.current
    val theme by vm.themeMode.collectAsState()
    val animations by vm.animations.collectAsState()
    var biometric by remember { mutableStateOf(vm.biometricEnabled()) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    SettingsContent(
        theme, animations, biometric, version ?: "—", onBack, onReveal, onChangePin,
        onReset, onNetwork, onWallets, vm::setTheme, vm::setAnimations,
        onBiometric = { enabled ->
            if (!enabled) {
                vm.setBiometric(false)
                biometric = false
            } else {
                val activity = context as? FragmentActivity
                if (activity != null) {
                    authenticate(activity, "Use biometrics for ZKAPriv", "Confirm it is you", onSuccess = {
                        vm.setBiometric(true)
                        biometric = true
                    })
                }
            }
        },
        onOpen = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) },
    )
}

/** Ajustes sin almacén: la vista de diseño usa los mismos controles. */
@Composable
internal fun SettingsContent(
    theme: ThemeMode,
    animations: Boolean,
    biometric: Boolean,
    version: String,
    onBack: (() -> Unit)?,
    onReveal: (String) -> Unit,
    onChangePin: () -> Unit,
    onReset: () -> Unit,
    onNetwork: () -> Unit,
    onWallets: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onBiometric: (Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    ZkScreen(title = "Settings", onBack = onBack) {
        SettingsGroup("Wallets") {
            SettingRow("Your wallets", onWallets, Icons.Outlined.AccountBalanceWallet, sub = "Switch, create or restore")
        }
        SettingsGroup("Security") {
            SettingRow("Recovery phrase", { onReveal("phrase") }, Icons.Outlined.Key, sub = "Your wallet's 24 words")
            RowDivider()
            SettingRow("View keys", { onReveal("viewkey") }, Icons.Outlined.Visibility, sub = "Choose what to share")
            RowDivider()
            SettingRow("Change PIN", onChangePin, Icons.Outlined.Lock)
            RowDivider()
            SwitchRow("Unlock with biometrics", null, biometric, Icons.Outlined.Fingerprint, onChange = onBiometric)
        }
        HintText("Locks when you leave the app or after 5 minutes without use.", Modifier.padding(horizontal = 4.dp))
        SettingsGroup("Network") {
            SettingRow("Network & Tor", onNetwork, Icons.Outlined.Public, sub = "Connection and privacy")
        }
        SettingsGroup("Appearance") {
            Column(Modifier.padding(14.dp)) {
                Segmented(
                    listOf("Dark", "Light", "System"),
                    when (theme) {
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.SYSTEM -> "System"
                    },
                    { onTheme(when (it) { "Light" -> ThemeMode.LIGHT; "System" -> ThemeMode.SYSTEM; else -> ThemeMode.DARK }) },
                    Modifier.fillMaxWidth(),
                )
            }
            RowDivider()
            SwitchRow("Animations", "Respects reduced motion", animations, Icons.Outlined.Animation, onChange = onAnimations)
        }
        SettingsGroup("About") {
            ValueRow("Version", version, Icons.Outlined.Info)
            RowDivider()
            SettingRow("zoka.network", { onOpen("https://zoka.network") }, external = true)
            RowDivider()
            SettingRow("ZokaScan explorer", { onOpen("https://zokascan.cc") }, external = true)
            RowDivider()
            SettingRow("Source code", { onOpen("https://github.com/zoka-network/zkapriv") }, external = true)
            RowDivider()
            SettingRow("Privacy", { onOpen("https://github.com/zoka-network/zkapriv/blob/main/PRIVACY_POLICY_EN.md") }, external = true)
        }
        SettingsGroup("Danger zone") {
            SettingRow("Remove wallets", onReset, Icons.Outlined.DeleteOutline, sub = "From this phone", danger = true)
        }
        VSpace(8)
    }
}

/**
 * Pide el PIN (o la huella) y recién entonces muestra la frase o la clave de
 * vista. Las capturas de pantalla están bloqueadas en toda la app.
 */
@Composable
fun RevealScreen(vm: WalletViewModel, what: String, onBack: () -> Unit) {
    val walletId by vm.activeWallet.collectAsState()
    // Ni la autenticación ni un resultado tardío sobreviven al cambio de billetera.
    key(walletId, what) {
        WalletRevealScreen(vm, walletId, what, onBack)
    }
}

@Composable
private fun WalletRevealScreen(vm: WalletViewModel, walletId: String, what: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var viewScope by remember { mutableStateOf(ViewKeyScope.INCOMING) }
    var exportedKey by remember(viewScope) { mutableStateOf<String?>(null) }
    var exportError by remember(viewScope) { mutableStateOf<String?>(null) }
    val title = if (what == "phrase") "Recovery phrase" else "View keys"
    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity
        if (vm.biometricEnabled() && activity != null && vm.pinWaitMillis() == 0L) {
            authenticate(activity, "Show $title", "Confirm it is you", onSuccess = { unlocked = true })
        }
    }
    LaunchedEffect(unlocked, viewScope) {
        if (unlocked && what != "phrase") {
            try {
                exportedKey = vm.viewKey(walletId, viewScope)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // No propagar errores nativos que pudieran contener datos sensibles.
                exportError = "Could not load this view key. Go back and try again with the latest ZKAPriv version."
            }
        }
    }
    if (!unlocked) {
        ZkScreen(title = title, onBack = onBack, scroll = false) {
            PinPrompt(
                title = "Enter your PIN",
                check = vm::checkPin,
                waitMillis = vm::pinWaitMillis,
                onOk = { unlocked = true },
                onBiometric = if (vm.biometricEnabled() && context is FragmentActivity) ({
                    authenticate(context, "Show $title", "Confirm it is you", onSuccess = { unlocked = true })
                }) else null,
            )
        }
        return
    }
    ZkScreen(title = title, onBack = onBack) {
        if (what == "phrase") {
            Notice(
                "Anyone with these words can spend your coins. Write them on paper; never in a chat, email or photo.",
                tone = ZkTone.Err,
            )
            WordGrid(vm.phrase().orEmpty().split(" "))
        } else {
            Segmented(
                listOf("Incoming", "Full"),
                if (viewScope == ViewKeyScope.INCOMING) "Incoming" else "Full",
                { viewScope = if (it == "Full") ViewKeyScope.FULL else ViewKeyScope.INCOMING },
                Modifier.fillMaxWidth(),
            )
            Notice(
                if (viewScope == ViewKeyScope.INCOMING)
                    "Shows incoming payments and their memos. It cannot show spending or your current balance, and cannot spend coins."
                else
                    "Shows incoming and outgoing payments, memos and your balance. Share only with someone you trust with your full history. It cannot spend coins.",
                title = viewScope.title,
                tone = ZkTone.Warn,
            )
            if (viewScope == ViewKeyScope.INCOMING) {
                HintText("Change is excluded automatically for payments made with updated wallets. Older wallets did not label change, so earlier change can appear as an incoming payment. Incoming totals are not a balance.")
            }
            when {
                exportError != null -> ErrorText(exportError!!)
                exportedKey == null -> HintText("Loading view key…")
                else -> Panel {
                    val value = exportedKey!!
                    MonoText(value, maxLines = 12)
                    CopyButton(value, Modifier.fillMaxWidth(), label = "Copy ${viewScope.title.lowercase()}")
                }
            }
        }
    }
}

/** Cambiar el PIN: primero el actual, después el nuevo dos veces. */
@Composable
fun ChangePinScreen(vm: WalletViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    if (verified) {
        PinSetupScreen(onBack = onBack, onPinSet = {
            vm.setPin(it)
            onDone()
        })
        return
    }
    ZkScreen(title = "Change PIN", onBack = onBack, scroll = false) {
        PinPrompt(
            title = "Enter your current PIN",
            check = vm::checkPin,
            waitMillis = vm::pinWaitMillis,
            onOk = { verified = true },
        )
    }
}
