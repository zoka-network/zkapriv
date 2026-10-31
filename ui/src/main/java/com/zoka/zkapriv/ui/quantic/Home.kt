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
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    onReceive: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onMovement: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    ZkScreen(
        title = null,
        bottom = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZButton(onReceive, Modifier.weight(1f), ZButtonVariant.Accent, ZButtonSize.Large) {
                    Icon(Icons.Outlined.CallReceived, null, Modifier.size(18.dp))
                    ZButtonText("Receive")
                }
                ZButton(onSend, Modifier.weight(1f), ZButtonVariant.Primary, ZButtonSize.Large) {
                    Icon(Icons.Outlined.CallMade, null, Modifier.size(18.dp))
                    ZButtonText("Send")
                }
            }
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuanticWordmark(MaterialTheme.typography.titleLarge, Modifier.weight(1f), suffix = "TESTNET")
            IconButton(onClick = onScan) {
                Icon(Icons.Outlined.QrCodeScanner, "Scan a QR code", tint = Zk.colors.text)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, "Settings", tint = Zk.colors.text)
            }
        }
        BalancePanel(state, vm.torMode())
        state.error?.let { error ->
            Notice(
                error,
                title = "Could not read the chain",
                tone = ZkTone.Err,
                action = {
                    ZButton({ vm.syncNow() }, size = ZButtonSize.Small) { ZButtonText("Try again") }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Recent", Modifier.weight(1f))
            if (state.history.isNotEmpty()) {
                ZButton(onHistory, variant = ZButtonVariant.Ghost, size = ZButtonSize.Small) {
                    ZButtonText("All")
                }
            }
        }
        if (state.history.isEmpty()) {
            EmptyState(
                if (state.loaded) "No movements yet" else "Reading the chain…",
                sub = "Payments you send or receive show up here.",
            )
        } else {
            Panel(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)) {
                state.history.take(8).forEachIndexed { i, movement ->
                    if (i > 0) ThinDivider()
                    MovementRow(movement) { onMovement(movement.txid) }
                }
            }
        }
    }
}

@Composable
private fun BalancePanel(state: QuanticState, tor: Boolean) {
    Panel(
        title = "Balance",
        right = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (tor) Pill("Tor", tone = ZkTone.Info)
                SyncPill(state)
            }
        },
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                formatZka(state.balanceAtoms),
                style = Zk.text.monoDisplay,
                color = Zk.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text("ZKA", style = Zk.text.mono, color = Zk.colors.accent, modifier = Modifier.padding(bottom = 6.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Spendable", formatZka(state.spendableAtoms), Modifier.weight(1f))
            Stat(
                "Maturing",
                formatZka(state.immatureAtoms),
                Modifier.weight(1f),
                hint = if (state.immatureAtoms > 0) "Mined rewards wait 60 blocks" else null,
            )
        }
        if (state.reservedAtoms > 0) {
            KeyValueRow("In a pending payment", formatZka(state.reservedAtoms) + " ZKA")
        }
        if (!state.inSync || state.syncing) {
            MeterBar(state.progress)
        }
        Text(
            when {
                state.tip == 0 -> "Waiting for the network"
                state.inSync -> "Block ${"%,d".format(state.height)}" + (state.source?.let { " · via ${sourceName(it)}" } ?: "")
                else -> "Block ${"%,d".format(state.height)} of ${"%,d".format(state.tip)}"
            },
            style = Zk.text.mono,
            color = Zk.colors.textMuted,
        )
    }
}

private fun sourceName(source: String) = when (source) {
    "https" -> "zokascan.cc"
    "tor" -> "Tor"
    else -> "P2P"
}

@Composable
private fun SyncPill(state: QuanticState) = when {
    state.error != null && !state.syncing -> Pill("Offline", tone = ZkTone.Err)
    state.syncing || !state.inSync -> Pill("Syncing", tone = ZkTone.Warn)
    else -> Pill("Synced", tone = ZkTone.On)
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
fun HistoryScreen(vm: WalletViewModel, onBack: () -> Unit, onMovement: (String) -> Unit) {
    val state by vm.state.collectAsState()
    ZkScreen(title = "Movements", onBack = onBack) {
        if (state.history.isEmpty()) {
            EmptyState("No movements yet", sub = "Payments you send or receive show up here.")
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
