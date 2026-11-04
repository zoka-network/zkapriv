package com.zoka.zkapriv.ui.quantic

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.KeyValueRow
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk
import java.text.DateFormat
import java.util.Date

/** La red: estado, de dónde lee la billetera, verificación y Modo Tor. */
@Composable
fun NetworkScreen(vm: WalletViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    var tor by remember { mutableStateOf(vm.torMode()) }
    NetworkContent(state, tor, onBack) { enabled ->
        tor = enabled
        vm.setTorMode(enabled)
    }
}

/** También la usa la vista de diseño, con datos ficticios. */
@Composable
internal fun NetworkContent(state: QuanticState, tor: Boolean, onBack: () -> Unit, onTor: (Boolean) -> Unit) {
    ZkScreen(title = "Network", onBack = onBack) {
        NetworkCard(state)
        Panel(title = "Your wallet") {
            KeyValueRow("Network", "Zoka Quantic Testnet v2")
            KeyValueRow("Blocks read", if (state.tip > 0) "%,d / %,d".format(state.height, state.tip) else "—")
            KeyValueRow(
                "Verified through",
                if (state.verifiedHeight > 0) "block %,d".format(state.verifiedHeight) else "waiting",
            )
            KeyValueRow("Reads from", sourceName(state.source))
            KeyValueRow("Known peers", state.peers.toString())
            KeyValueRow(
                "Last update",
                if (state.lastSyncMillis > 0) DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.lastSyncMillis)) else "—",
            )
            HintText(
                "It updates by itself. Every page is checked against its block header and the team's " +
                    "hybrid checkpoint, so a server cannot invent your balance.",
            )
        }
        Panel(title = "Privacy") {
            SwitchRow(
                "Tor mode",
                "Reach the network only through Tor, to the onion seeds. Slower; your IP stays hidden.",
                tor,
                onTor,
            )
            if (tor) {
                Notice(
                    "Tor starts inside ZKAPriv. If it cannot start, the wallet stays offline: it never connects without it.",
                    tone = ZkTone.Info,
                )
            }
            ThinDivider()
            KeyValueRow("Connection", if (tor) "Via Tor" else "Direct")
            HintText("Your phone generates every proof. Amounts and recipients stay private; without Tor, the seeds see your IP.")
        }
        Text(
            "Test network · Testnet ZKA have no value.",
            style = MaterialTheme.typography.bodySmall,
            color = Zk.colors.textMuted,
        )
    }
}

internal fun sourceName(source: String?) = when (source) {
    "https" -> "zokascan.cc"
    "tor" -> "onion seed (Tor)"
    "p2p" -> "seed (P2P)"
    else -> "—"
}
