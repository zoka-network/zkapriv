package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zoka.zkapriv.network.quantic.QuanticMovement
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.network.quantic.WalletAddress
import com.zoka.zkapriv.core.preferences.ThemeMode
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.qrscanner.ScannerOverlayPreview
import com.zoka.zkapriv.ui.theme.ZkTheme

/** Solo accesible desde la actividad debug. Datos ficticios, sin abrir el almacén. */
@Composable
fun WalletDesignPreview(dark: Boolean, scenario: String) {
    var selected by remember { mutableStateOf(if (scenario == "working") "proof" else scenario) }
    var tor by remember { mutableStateOf(false) }
    var theme by remember { mutableStateOf(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
    var animations by remember { mutableStateOf(true) }
    var biometric by remember { mutableStateOf(false) }
    var addressIndex by remember { mutableStateOf(0) }
    var addresses by remember {
        mutableStateOf(List(if (scenario == "receive-eight") 8 else 2) { sampleAddress(it) })
    }
    var form by remember {
        mutableStateOf(SendFormState(
            to = sampleAddress(0).short, kind = "short", amountText = "25", amountAtoms = 25_000_000,
            quoted = true, enough = true, feeAtoms = 200, spendableAtoms = 12_438_000_000,
            maxSendableAtoms = 12_437_999_800,
        ))
    }
    val sample = remember { sampleState() }
    val words = remember { List(23) { "abandon" } + "art" }
    val startedAt = remember { System.currentTimeMillis() - 12_000 }
    val back = { selected = "home" }
    val pinCheck: (String) -> QuanticVault.PinCheck = { QuanticVault.PinCheck.Wrong(4) }
    val tabs = selected in walletRoutes || selected == "receive-eight"
    ZkTheme(darkTheme = theme != ThemeMode.LIGHT, animations = animations) {
        Column(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalWalletTabs provides tabs) {
                Box(Modifier.weight(1f)) {
                    when (selected) {
                        "home" -> HomeContent(
                            sample, false, "Main wallet", onWallets = { selected = "wallets" },
                            onScan = { selected = "scan" }, onNetwork = { selected = "network" }, onSendProgress = {},
                        )
                        "welcome" -> WelcomeScreen({ selected = "backup" }, { selected = "restore" })
                        "backup" -> BackupPhraseScreen(words, back) { selected = "verify" }
                        "verify" -> VerifyPhraseScreen(words, back) { selected = "pin" }
                        "restore" -> RestoreScreen({ it == words.joinToString(" ") }, back) { selected = "pin" }
                        "pin" -> PinSetupScreen(back, { selected = "biometric" }, steps = 3)
                        "biometric" -> BiometricOfferContent(null, back, back)
                        "lock" -> LockContent(pinCheck, { 0L }, back, {}, { selected = "reset" })
                        "reset", "reset-sending" -> ResetContent(selected == "reset-sending", false, null, back) { selected = "welcome" }
                        "reveal", "changepin", "send-pin" -> ZkScreen(title = when (selected) {
                            "changepin" -> "Change PIN"; "send-pin" -> "Authorize payment"; else -> "Recovery phrase"
                        }, onBack = back, scroll = false) {
                            PinPrompt("Enter your PIN", pinCheck, { 0L }, back)
                        }
                        "scan" -> ScannerOverlayPreview()
                        "wallets" -> WalletsContent(
                            listOf(QuanticVault.WalletEntry("main", "Main wallet"), QuanticVault.WalletEntry("a1b2c3d4", "Savings")),
                            active = "main", busy = false, sending = false,
                            onBack = back, onSelect = {}, onEdit = { selected = "wallet-edit" },
                            onCreate = { selected = "backup" }, onRestore = { selected = "restore" },
                        )
                        "wallet-edit" -> {
                            EditWalletDialog(QuanticVault.WalletEntry("sample", "Savings"), true,
                                onRename = { Result.success(Unit) }, onRemove = { it(Result.success(Unit)) }, onDismiss = back)
                        }
                        "network" -> NetworkContent(sample.copy(torEnabled = tor), tor, onBack = back) { tor = it }
                        "history" -> HistoryContent(sample.history, onMovement = { selected = "movement" })
                        "movement" -> MovementContent(sample.history[1], back, {})
                        "receive", "receive-eight" -> ReceiveContent(
                            addresses, addressIndex, false, null, { addressIndex = it },
                            onAdd = { val next = addresses.size; addresses = addresses + sampleAddress(next); addressIndex = next },
                            onShare = {},
                        )
                        "send" -> SendFormContent(
                            form, { form = form.copy(to = it) }, { form = form.copy(amountText = it) },
                            { form = form.copy(memo = it) }, {}, { selected = "scan" },
                            { form = form.copy(amountText = "12437.9998") }, { selected = "review" },
                        )
                        "review" -> SendReviewContent(form, tor, { selected = "send" }, { selected = "send-pin" })
                        "proof" -> SendWorkingContent(2, startedAt)
                        "accepted" -> SendDoneContent(25_000_000, 200, 14_000, 0, "a1".repeat(32), back)
                        "failed" -> SendFailedContent("Could not connect. Check your connection and try again.", false, back)
                        "settings" -> SettingsContent(
                            theme, animations, biometric, "4.1.0", null, { selected = "reveal" },
                            { selected = "changepin" }, { selected = "reset" }, { selected = "network" },
                            { selected = "wallets" }, { theme = it }, { animations = it }, { biometric = it }, {},
                        )
                        else -> WelcomeScreen({ selected = "home" }, { selected = "restore" })
                    }
                }
            }
            if (tabs) WalletNavigation(if (selected == "receive-eight") "receive" else selected) { selected = it }
        }
    }
}

/** No es una dirección real: estos QR solo sirven para revisar el diseño. */
private fun sampleAddress(index: Int) = WalletAddress(
    index, "zqtest2SampleOnly" + "abcd1234".repeat(5) + index,
    "zqtest2SampleOnly" + "abcd1234".repeat(160) + index, true,
)

private fun sampleState(): QuanticState {
    val now = System.currentTimeMillis() / 1000
    return QuanticState(
        loaded = true, height = 4021, tip = 4021, inSync = true,
        spendableAtoms = 12_438_000_000, immatureAtoms = 12_000_000,
        verifiedHeight = 4020, peers = 3, source = "https", lastSyncMillis = System.currentTimeMillis(),
        hashrateHps = 841,
        history = listOf(
            QuanticMovement("a1".repeat(32), 4019, now - 3_600, "received", 250_000_000, 0, "", 3),
            QuanticMovement("b2".repeat(32), 3310, now - 26 * 3_600, "sent", 48_000_000, 200, "", 712),
            QuanticMovement("c3".repeat(32), 1290, now - 9 * 24 * 3_600, "mined", 12_000_000, 0, "", 2_732),
        ),
    )
}
