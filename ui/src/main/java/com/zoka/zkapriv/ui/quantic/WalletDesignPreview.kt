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
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.qrscanner.ScannerOverlayPreview
import com.zoka.zkapriv.ui.theme.ZkTheme

/** Solo accesible desde la actividad debug. Datos ficticios, sin abrir el almacén. */
@Composable
fun WalletDesignPreview(dark: Boolean, scenario: String) {
    val screens = setOf("home", "network", "history", "scan", "wallets")
    var selected by remember { mutableStateOf(if (scenario in screens) scenario else "send") }
    var phase by remember { mutableStateOf(scenario) }
    var tor by remember { mutableStateOf(false) }
    val sample = remember { sampleState() }
    ZkTheme(darkTheme = dark) {
        Column(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalWalletTabs provides (selected !in setOf("network", "scan", "wallets"))) {
                Box(Modifier.weight(1f)) {
                    when (selected) {
                        "home" -> HomeContent(
                            sample, false, "Main wallet", onWallets = { selected = "wallets" },
                            onScan = { selected = "scan" }, onNetwork = { selected = "network" }, onSendProgress = {},
                            sample = true,
                        )
                        "scan" -> ScannerOverlayPreview()
                        "wallets" -> WalletsContent(
                            listOf(QuanticVault.WalletEntry("main", "Main wallet"), QuanticVault.WalletEntry("a1b2c3d4", "Savings")),
                            active = "main", busy = false, sending = false,
                            onBack = { selected = "home" }, onSelect = {}, onEdit = {}, onCreate = {}, onRestore = {},
                        )
                        "network" -> NetworkContent(sample.copy(torEnabled = tor), tor, onBack = { selected = "home" }) { tor = it }
                        "history" -> HistoryContent(sample.history, onMovement = {})
                        else -> ZkScreen(title = "Design preview · Sample data") {
                            val result = when (phase) {
                                "accepted" -> ProofResult.Accepted
                                "failed" -> ProofResult.Failed
                                else -> ProofResult.Working
                            }
                            ProofMatrix(result)
                            SectionTitle("ZOKA QUANTIC · STARK", when (result) {
                                ProofResult.Working -> "Generating the proof…"
                                ProofResult.Accepted -> "Payment accepted"
                                ProofResult.Failed -> "Could not send"
                            }, "Animation preview. No transactions are generated or sent.")
                            ZButton({ phase = when (phase) { "accepted" -> "failed"; "failed" -> "working"; else -> "accepted" } }) {
                                ZButtonText("Next state")
                            }
                        }
                    }
                }
            }
            if (selected !in setOf("network", "scan", "wallets")) {
                WalletNavigation(selected) { if (it != "receive" && it != "settings") selected = it }
            }
        }
    }
}

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
