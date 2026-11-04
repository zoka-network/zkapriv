package com.zoka.zkapriv.ui.quantic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.CopyButton
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.KeyValueRow
import com.zoka.zkapriv.ui.components.MonoText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Segmented
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZToggle
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    vm: WalletViewModel,
    onBack: () -> Unit,
    onReveal: (String) -> Unit,
    onChangePin: () -> Unit,
    onWiped: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsState()
    val sending by vm.sendState.collectAsState()
    val theme by vm.themeMode.collectAsState()
    val animations by vm.animations.collectAsState()
    var biometric by remember { mutableStateOf(vm.biometricEnabled()) }
    var tor by remember { mutableStateOf(vm.torMode()) }
    var confirmWipe by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    ZkScreen(title = "Settings", onBack = onBack) {
        Panel(title = "Security") {
            SettingRow("Recovery phrase", "The 24 words. Ask for your PIN first.") { onReveal("phrase") }
            ThinDivider()
            SettingRow("View key", "Shows your balance to whoever has it, without spending.") { onReveal("viewkey") }
            ThinDivider()
            SettingRow("Change PIN", null, onChangePin)
            ThinDivider()
            SwitchRow("Unlock with biometrics", null, biometric) { enabled ->
                if (!enabled) {
                    vm.setBiometric(false)
                    biometric = false
                } else {
                    val activity = context as? FragmentActivity ?: return@SwitchRow
                    authenticate(activity, "Use biometrics for ZKAPriv", "Confirm it is you", onSuccess = {
                        vm.setBiometric(true)
                        biometric = true
                    })
                }
            }
            HintText("ZKAPriv locks itself when you leave it and after 5 minutes without use.")
        }

        Panel(title = "Network") {
            SwitchRow(
                "Tor mode",
                "Reach the network only through Tor, to the onion seeds. Slower; your IP stays hidden.",
                tor,
            ) { enabled ->
                tor = enabled
                vm.setTorMode(enabled)
            }
            if (tor) {
                Notice(
                    "Tor starts inside ZKAPriv. If it cannot start, the wallet stays offline: it never connects without it.",
                    tone = ZkTone.Info,
                )
            }
            ThinDivider()
            KeyValueRow("Network", "Zoka Quantic Testnet v2")
            KeyValueRow("Block", "%,d".format(state.height))
            KeyValueRow(
                "Reads from",
                when (state.source) {
                    "https" -> "zokascan.cc"
                    "tor" -> "onion seed (Tor)"
                    "p2p" -> "seed (P2P)"
                    else -> "—"
                },
            )
            ZButton({ vm.syncNow() }, Modifier.fillMaxWidth(), enabled = !state.syncing) {
                ZButtonText(if (state.syncing) "Syncing…" else "Sync now")
            }
        }

        Panel(title = "Appearance") {
            Segmented(
                listOf("Dark", "Light", "System"),
                when (theme) {
                    ThemeMode.DARK -> "Dark"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.SYSTEM -> "System"
                },
                { choice ->
                    vm.setTheme(
                        when (choice) {
                            "Light" -> ThemeMode.LIGHT
                            "System" -> ThemeMode.SYSTEM
                            else -> ThemeMode.DARK
                        },
                    )
                },
                Modifier.fillMaxWidth(),
            )
            SwitchRow("Animations", "Off also respects the system setting.", animations) { vm.setAnimations(it) }
        }

        Panel(title = "About") {
            KeyValueRow("Version", version ?: "—")
            SettingRow("zoka.network", null) { open("https://zoka.network") }
            ThinDivider()
            SettingRow("ZokaScan explorer", null) { open("https://zokascan.cc") }
            ThinDivider()
            SettingRow("Source code", null) { open("https://github.com/zoka-network/zkapriv") }
            ThinDivider()
            SettingRow("Privacy", null) { open("https://github.com/zoka-network/zkapriv/blob/zkapriv-v2/PRIVACY_POLICY_EN.md") }
            HintText("Payments are private: amounts, senders and receivers are not public. The seeds you connect to see your IP unless Tor mode is on.")
        }

        Panel(title = "Danger zone") {
            if (sending.running) {
                Notice("Wait until your payment finishes before removing the wallet.", tone = ZkTone.Warn)
            } else {
                Text(
                    "Remove this wallet from the phone. Only the 24 words bring it back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Zk.colors.textMuted,
                )
                if (!confirmWipe) {
                    ZButton({ confirmWipe = true }, Modifier.fillMaxWidth(), ZButtonVariant.Danger) {
                        ZButtonText("Danger zone")
                    }
                } else {
                    Notice("Did you write down the 24 words? Without them the coins are lost.", tone = ZkTone.Err)
                    Row {
                        ZButton({ confirmWipe = false }, Modifier.weight(1f), ZButtonVariant.Ghost) { ZButtonText("Cancel") }
                        ZButton(
                            { scope.launch { vm.wipe(); onWiped() } },
                            Modifier.weight(1f),
                            ZButtonVariant.Danger,
                        ) { ZButtonText("Remove") }
                    }
                }
            }
        }
        VSpace(8)
    }
}

@Composable
private fun SettingRow(title: String, sub: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Zk.colors.text)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = Zk.colors.textMuted)
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = Zk.colors.text)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
        }
        ZToggle(checked, onChange, description = title)
    }
}

/**
 * Pide el PIN (o la huella) y recién entonces muestra la frase o la clave de
 * vista. Las capturas de pantalla están bloqueadas en toda la app.
 */
@Composable
fun RevealScreen(vm: WalletViewModel, what: String, onBack: () -> Unit) {
    val context = LocalContext.current
    var unlocked by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val title = if (what == "phrase") "Recovery phrase" else "View key"
    LaunchedEffect(Unit) {
        val activity = context as? FragmentActivity
        if (vm.biometricEnabled() && activity != null) {
            authenticate(activity, "Show $title", "Confirm it is you", onSuccess = { unlocked = true })
        }
    }
    LaunchedEffect(pin) {
        if (pin.length == QuanticVault.PIN_LENGTH) {
            when (val check = vm.checkPin(pin)) {
                QuanticVault.PinCheck.Ok -> unlocked = true
                is QuanticVault.PinCheck.Wrong -> error = "Wrong PIN."
                is QuanticVault.PinCheck.Wait -> error = "Too many wrong PINs. Wait ${(check.millis + 999) / 1000} s."
            }
            pin = ""
        }
    }
    if (!unlocked) {
        ZkScreen(title = title, onBack = onBack, scroll = false) {
            Text("Enter your PIN", style = MaterialTheme.typography.bodyLarge, color = Zk.colors.textMuted)
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PinDots(pin.length, error = error != null && pin.isEmpty())
                VSpace(12)
                error?.let { ErrorText(it) }
                VSpace(20)
                PinPad(
                    onDigit = {
                        error = null
                        if (pin.length < QuanticVault.PIN_LENGTH) pin += it
                    },
                    onDelete = { pin = pin.dropLast(1) },
                )
            }
            VSpace(12)
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
            val key = remember { vm.viewKey() }
            Notice(
                "Whoever has this key sees your balance and history (on zokascan.cc/wallet, say), but cannot spend.",
                tone = ZkTone.Warn,
            )
            Panel {
                MonoText(key, maxLines = 12)
                CopyButton(key, Modifier.fillMaxWidth(), label = "Copy view key")
            }
        }
    }
}

/** Cambiar el PIN: primero el actual, después el nuevo dos veces. */
@Composable
fun ChangePinScreen(vm: WalletViewModel, onBack: () -> Unit, onDone: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    if (verified) {
        PinSetupScreen(onBack = onBack, onPinSet = {
            vm.setPin(it)
            onDone()
        })
        return
    }
    LaunchedEffect(pin) {
        if (pin.length == QuanticVault.PIN_LENGTH) {
            when (val check = vm.checkPin(pin)) {
                QuanticVault.PinCheck.Ok -> verified = true
                is QuanticVault.PinCheck.Wrong -> error = "Wrong PIN."
                is QuanticVault.PinCheck.Wait -> error = "Too many wrong PINs. Wait ${(check.millis + 999) / 1000} s."
            }
            pin = ""
        }
    }
    ZkScreen(title = "Change PIN", onBack = onBack, scroll = false) {
        Text("Enter your current PIN", style = MaterialTheme.typography.bodyLarge, color = Zk.colors.textMuted)
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PinDots(pin.length, error = error != null && pin.isEmpty())
            VSpace(12)
            error?.let { ErrorText(it) }
            VSpace(20)
            PinPad(
                onDigit = {
                    error = null
                    if (pin.length < QuanticVault.PIN_LENGTH) pin += it
                },
                onDelete = { pin = pin.dropLast(1) },
            )
        }
        VSpace(12)
    }
}
