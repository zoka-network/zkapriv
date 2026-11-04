package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.theme.ZkTheme

/** Solo accesible desde la actividad debug. Datos ficticios, sin abrir el almacén. */
@Composable
fun WalletDesignPreview(dark: Boolean, scenario: String) {
    var selected by remember { mutableStateOf(if (scenario == "home") "home" else "send") }
    var phase by remember { mutableStateOf(scenario) }
    ZkTheme(darkTheme = dark) {
        Column(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalWalletTabs provides true) {
                Box(Modifier.weight(1f)) {
                    if (selected == "home") HomeContent(
                        QuanticState(loaded = true, height = 4023, tip = 4023, inSync = true,
                            spendableAtoms = 128_450_000, immatureAtoms = 2_000_000,
                            verifiedHeight = 4000, peers = 3, source = "https", lastSyncMillis = System.currentTimeMillis()),
                        false, { selected = "send" }, {}, {}, sample = true,
                    ) else ZkScreen(title = "Design preview · Sample data") {
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
            WalletNavigation(selected) { selected = it }
        }
    }
}
