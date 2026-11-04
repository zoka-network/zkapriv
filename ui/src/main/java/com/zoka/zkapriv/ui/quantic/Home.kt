package com.zoka.zkapriv.ui.quantic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoka.zkapriv.network.quantic.QuanticMovement
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.network.quantic.QuanticWallet.Companion.formatZka
import com.zoka.zkapriv.ui.components.CopyableKeyValueRow
import com.zoka.zkapriv.ui.components.EmptyState
import com.zoka.zkapriv.ui.components.Eyebrow
import com.zoka.zkapriv.ui.components.KeyValueRow
import com.zoka.zkapriv.ui.components.MeterBar
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.QuanticWordmark
import com.zoka.zkapriv.ui.components.Stat
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk
import java.text.DateFormat
import java.util.Date

const val EXPLORER = "https://zokascan.cc"

@Composable
fun HomeScreen(
    vm: WalletViewModel,
    onSend: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val send by vm.sendState.collectAsState()
    HomeContent(state, send.running, onSend, onSettings, vm::syncNow)
}

/** También se usa en la vista de diseño, con datos ficticios y sin billetera. */
@Composable
internal fun HomeContent(state: QuanticState, sending: Boolean, onSend: () -> Unit, onSettings: () -> Unit, onRefresh: () -> Unit,
    sample: Boolean = false) {
    ZkScreen(title = null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuanticWordmark(MaterialTheme.typography.titleLarge, Modifier.weight(1f), suffix = "TESTNET")
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, "Settings", tint = Zk.colors.textMuted)
            }
        }
        if (sample) Text("Design preview · Sample balance", style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
        BalancePanel(state, onRefresh)
        if (state.error != null) {
            Notice(
                "Showing your last known balance. We will retry automatically.",
                title = "Out of date",
                tone = ZkTone.Warn,
                action = {
                    ZButton(onRefresh, size = ZButtonSize.Small) { ZButtonText("Refresh") }
                },
            )
        }
        PrivacyPanel(state)
        if (sending || state.pendingTxid != null) {
            Notice(
                if (sending) "Your phone is still preparing and sending the payment." else "Your available balance updates when the payment is included in a block.",
                title = if (sending) "Preparing your payment" else "Payment awaiting confirmation",
                tone = ZkTone.Info,
                action = {
                    if (sending) ZButton(onSend, size = ZButtonSize.Small) { ZButtonText("View progress") }
                }
            )
        }
        Text("Test network · Testnet ZKA have no value.", style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
    }
}

@Composable
private fun BalancePanel(state: QuanticState, onRefresh: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Total balance", Modifier.weight(1f))
            SyncPill(state)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val balance = if (state.loaded) formatZka(state.balanceAtoms) else "—"
            Text(
                balance,
                style = Zk.text.monoDisplay.copy(fontSize = if (balance.length > 12) 25.sp else 32.sp),
                color = Zk.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("ZKA", style = Zk.text.mono, color = Zk.colors.accent, modifier = Modifier.padding(bottom = 6.dp))
        }
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Available", if (state.loaded) formatZka(state.spendableAtoms) else "—", Modifier.weight(1f))
                Stat(
                    "Maturing",
                    if (state.loaded) formatZka(state.immatureAtoms) else "—",
                    Modifier.weight(1f),
                )
            }
            if (state.reservedAtoms > 0) {
                KeyValueRow("Reserved", formatZka(state.reservedAtoms) + " ZKA")
            }
        }
        if (!state.inSync) {
            MeterBar(state.progress)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    state.syncing && !state.inSync -> "Reading block ${state.height} of ${state.tip}"
                    state.lastSyncMillis > 0 -> "Updated ${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(state.lastSyncMillis))}"
                    else -> "Waiting for the network…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Zk.colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRefresh, enabled = !state.syncing) {
                Icon(Icons.Outlined.Refresh, "Refresh balance", tint = Zk.colors.accent)
            }
        }
    }
}

@Composable
private fun PrivacyPanel(state: QuanticState) {
    var details by rememberSaveable { mutableStateOf(false) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Outlined.Shield, null, tint = Zk.colors.accent, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f)) {
                Text("Quantic privacy", color = Zk.colors.text, style = MaterialTheme.typography.titleMedium)
                Text("STARK proofs · Hybrid X-Wing encryption", color = Zk.colors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        ThinDivider()
        KeyValueRow("Connection", if (state.torEnabled) "Via Tor" else "Direct")
        Text(if (state.verifiedHeight > 0) "Verified through block ${state.verifiedHeight}"
            else "Waiting for chain verification", style = Zk.text.mono, color = Zk.colors.textMuted)
        ZButton({ details = !details }, variant = ZButtonVariant.Ghost, size = ZButtonSize.Small) {
            ZButtonText(if (details) "Hide network details" else "Network details")
        }
        if (details) {
            Text("Your phone generates the proof. Amounts and recipients stay private.",
                style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
            if (state.immatureAtoms > 0) Text("Mining rewards mature after 60 blocks.",
                style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
            KeyValueRow("Blocks read", "${state.height} / ${state.tip}")
            KeyValueRow("Source", state.source?.let(::sourceName) ?: "Waiting")
            KeyValueRow("Known peers", state.peers.toString())
            Text("Tor hides your IP from peers. You can enable it in Settings.",
                style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
        }
    }
}

private fun sourceName(source: String) = when (source) {
    "https" -> "zokascan.cc"
    "tor" -> "Tor"
    else -> "P2P"
}

@Composable
private fun SyncPill(state: QuanticState) = when {
    state.error != null && !state.syncing -> Pill("Out of date", tone = ZkTone.Warn)
    state.lastSyncMillis == 0L -> Pill("Connecting", tone = ZkTone.Info)
    !state.inSync -> Pill("Syncing", tone = ZkTone.Warn)
    else -> Pill("Up to date", tone = ZkTone.On)
}

@Composable
fun MovementRow(movement: QuanticMovement, onClick: () -> Unit) {
    val c = Zk.colors
    val (icon, label, sign) = when (movement.kind) {
        "sent" -> Triple(Icons.Outlined.CallMade, "Sent", "−")
        "mined" -> Triple(Icons.Outlined.Bolt, "Mined", "+")
        else -> Triple(Icons.Outlined.CallReceived, "Received", "+")
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, tint = if (movement.kind == "sent") c.textMuted else c.accent, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(
                movement.memo.ifEmpty { shortDate(movement.timestamp) },
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                sign + formatZka(movement.amountAtoms),
                style = Zk.text.mono,
                color = if (movement.kind == "sent") c.text else c.success,
            )
            if (movement.confirmations < 3) {
                Text("${movement.confirmations} conf.", style = MaterialTheme.typography.bodySmall, color = c.warning)
            }
        }
    }
}

private fun shortDate(seconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(seconds * 1000))

@Composable
fun HistoryScreen(vm: WalletViewModel, onMovement: (String) -> Unit) {
    val state by vm.state.collectAsState()
    ZkScreen(title = "Activity", onBack = null, actions = {
        IconButton(onClick = vm::syncNow) { Icon(Icons.Outlined.Refresh, "Refresh activity", tint = Zk.colors.accent) }
    }) {
        if (state.history.isEmpty()) {
            EmptyState("No activity yet", sub = "Payments you send and receive appear here.")
        } else {
            Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                state.history.forEachIndexed { i, movement ->
                    if (i > 0) ThinDivider()
                    MovementRow(movement) { onMovement(movement.txid) }
                }
            }
        }
    }
}

@Composable
fun MovementScreen(vm: WalletViewModel, txid: String, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val movement = state.history.firstOrNull { it.txid == txid }
    ZkScreen(title = "Movement", onBack = onBack) {
        if (movement == null) {
            EmptyState("Not found", sub = "This movement is not in the wallet anymore.")
        } else {
            MovementDetail(movement) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$EXPLORER/block/${movement.height}")))
            }
        }
    }
}

@Composable
private fun MovementDetail(movement: QuanticMovement, onExplorer: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Zk.spacing.gap)) {
        Panel {
            Eyebrow(
                when (movement.kind) {
                    "sent" -> "Sent"
                    "mined" -> "Mined"
                    else -> "Received"
                },
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatZka(movement.amountAtoms), style = Zk.text.monoDisplay, color = Zk.colors.text)
                Text("ZKA", style = Zk.text.mono, color = Zk.colors.accent, modifier = Modifier.padding(bottom = 6.dp))
            }
            if (movement.memo.isNotEmpty()) {
                Notice(movement.memo, title = "Memo", tone = ZkTone.Info)
            }
            ThinDivider()
            if (movement.kind == "sent") KeyValueRow("Fee", formatZka(movement.feeAtoms) + " ZKA")
            KeyValueRow("Block", "%,d".format(movement.height))
            KeyValueRow("Confirmations", "%,d".format(movement.confirmations))
            KeyValueRow("Date", shortDate(movement.timestamp))
            CopyableKeyValueRow("Transaction id", movement.txid)
        }
        ZButton(
            onClick = onExplorer,
            modifier = Modifier.fillMaxWidth(),
            variant = ZButtonVariant.Outline,
        ) { ZButtonText("Open block in ZokaScan") }
        Text(
            "ZokaScan shows the block, not who paid whom: amounts and addresses stay private.",
            style = MaterialTheme.typography.bodySmall,
            color = Zk.colors.textMuted,
        )
    }
}
