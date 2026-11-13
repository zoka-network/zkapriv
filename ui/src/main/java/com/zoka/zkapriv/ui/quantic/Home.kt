package com.zoka.zkapriv.ui.quantic

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoka.zkapriv.network.quantic.QuanticMovement
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.network.quantic.QuanticWallet.Companion.formatZka
import com.zoka.zkapriv.ui.components.AccentDot
import com.zoka.zkapriv.ui.components.BrandMark
import com.zoka.zkapriv.ui.components.CheckBadge
import com.zoka.zkapriv.ui.components.CopyableKeyValueRow
import com.zoka.zkapriv.ui.components.EmptyState
import com.zoka.zkapriv.ui.components.Eyebrow
import com.zoka.zkapriv.ui.components.GlassIconButton
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.IconSquare
import com.zoka.zkapriv.ui.components.KeyValueRow
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.components.zkCapsule
import com.zoka.zkapriv.ui.components.zkGlass
import com.zoka.zkapriv.ui.components.zkPulseAlpha
import com.zoka.zkapriv.ui.theme.Zk
import java.text.DateFormat
import java.util.Date

const val EXPLORER = "https://zokascan.cc"

@Composable
fun HomeScreen(
    vm: WalletViewModel,
    onWallets: () -> Unit,
    onScan: () -> Unit,
    onNetwork: () -> Unit,
    onSendProgress: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val send by vm.sendState.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val active by vm.activeWallet.collectAsState()
    val name = wallets.firstOrNull { it.id == active }?.name ?: "Wallet"
    HomeContent(state, send.running, name, onWallets, onScan, onNetwork, onSendProgress)
}

/**
 * El inicio: el saldo y el estado de la red, nada más. Enviar, recibir, la
 * actividad y los ajustes están abajo. Se actualiza solo: no hay botón de
 * refrescar. También lo usa la vista de diseño, con datos ficticios.
 */
@Composable
internal fun HomeContent(
    state: QuanticState,
    sending: Boolean,
    walletName: String,
    onWallets: () -> Unit,
    onScan: () -> Unit,
    onNetwork: () -> Unit,
    onSendProgress: () -> Unit,
) {
    var hidden by rememberSaveable { mutableStateOf(false) }
    ZkScreen(title = null, scroll = false) {
        HomeTopBar(onScan)
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WalletChip(walletName, onWallets) }
        Balance(state, hidden) { hidden = !hidden }
        Spacer(Modifier.weight(1f))
        if (sending || state.pendingTxid != null) PendingStrip(sending, onSendProgress)
        NetworkCard(state, onClick = onNetwork)
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun HomeTopBar(onScan: () -> Unit) {
    val c = Zk.colors
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.text)) { append("ZKA") }
                    withStyle(SpanStyle(color = c.accent)) { append("Priv") }
                },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
            BrandMark()
        }
        GlassIconButton(Icons.Outlined.QrCodeScanner, "Scan a QR code", onScan)
    }
}

/** La billetera en uso; tocarla abre la lista para cambiarla o agregar otra. */
@Composable
private fun WalletChip(name: String, onClick: () -> Unit) {
    val c = Zk.colors
    Row(
        Modifier
            .zkCapsule()
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccentDot(size = 7.dp)
        Text(name, style = MaterialTheme.typography.labelLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Outlined.ExpandMore, "Change wallet", tint = c.textMuted, modifier = Modifier.size(18.dp))
    }
}

/** El saldo, grande y centrado, con el ojo para ocultarlo. */
@Composable
private fun Balance(state: QuanticState, hidden: Boolean, onToggle: () -> Unit) {
    val c = Zk.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PRIVATE BALANCE", style = Zk.text.eyebrow, color = c.textMuted, maxLines = 1, softWrap = false)
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onToggle),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    if (hidden) "Show balance" else "Hide balance",
                    tint = c.textMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
        val amount = when {
            !state.loaded -> "—"
            hidden -> "••••••"
            else -> displayZka(state.balanceAtoms)
        }
        FittedAmount(amount, max = 52.sp)
        // Lo que todavía no se puede gastar sigue en el total: se dice cuánto.
        if (state.loaded && !hidden && (state.immatureAtoms > 0 || state.reservedAtoms > 0)) {
            Text(
                listOfNotNull(
                    state.immatureAtoms.takeIf { it > 0 }?.let { "${displayZka(it)} maturing" },
                    state.reservedAtoms.takeIf { it > 0 }?.let { "${displayZka(it)} in a pending send" },
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Un envío en curso o esperando bloque. */
@Composable
private fun PendingStrip(sending: Boolean, onOpen: () -> Unit) {
    val c = Zk.colors
    val shape = MaterialTheme.shapes.large
    Row(
        Modifier
            .fillMaxWidth()
            .zkGlass(shape)
            .clip(shape)
            .clickable(enabled = sending, onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(8.dp).alpha(zkPulseAlpha()).background(if (sending) c.accent else c.warning, CircleShape))
        Text(
            if (sending) "Sending · generating the private proof" else "Payment sent · waiting for a block",
            style = MaterialTheme.typography.bodyMedium,
            color = c.text,
            modifier = Modifier.weight(1f),
        )
        if (sending) Icon(Icons.Outlined.ArrowForward, "View progress", tint = c.accent, modifier = Modifier.size(20.dp))
    }
}

private data class NetworkStatus(val label: String, val color: Color, val connected: Boolean)

@Composable
private fun networkStatus(state: QuanticState): NetworkStatus {
    val c = Zk.colors
    return when {
        state.error != null && !state.syncing -> NetworkStatus("OUT OF DATE", c.warning, false)
        state.lastSyncMillis == 0L -> NetworkStatus("CONNECTING", c.textMuted, false)
        !state.inSync -> NetworkStatus("SYNCING", c.accentSoft, false)
        else -> NetworkStatus("CONNECTED", c.accentSoft, true)
    }
}

/** "QUANTIC TESTNET · CONNECTED" con la altura, el hashrate y la sincronización. */
@Composable
internal fun NetworkCard(state: QuanticState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val c = Zk.colors
    val status = networkStatus(state)
    val label = Zk.text.panelLabel.copy(fontSize = 11.sp, letterSpacing = 1.4.sp)
    Panel(modifier, contentPadding = PaddingValues(0.dp), gap = 0.dp, hero = true, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            AccentDot(size = 7.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "QUANTIC TESTNET",
                style = label,
                color = c.text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status.label, style = label, color = status.color, maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            if (status.connected) {
                CheckBadge()
            } else {
                Box(Modifier.size(8.dp).alpha(zkPulseAlpha()).background(status.color, CircleShape))
            }
        }
        ThinDivider()
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
            val height = if (state.tip > 0) state.tip else state.height
            NetworkStat(Icons.Outlined.ViewInAr, "HEIGHT", if (height > 0) "#%,d".format(height) else "—", Modifier.weight(1f))
            StatDivider()
            NetworkStat(
                Icons.Outlined.Speed,
                "HASHRATE",
                if (state.torEnabled) "—" else displayHashrate(state.hashrateHps),
                Modifier.weight(1f),
            )
            StatDivider()
            NetworkStat(
                Icons.Outlined.Sync,
                "SYNC",
                when {
                    state.tip <= 0 -> "—"
                    state.inSync -> "100%"
                    else -> "${(state.progress * 100).toInt().coerceAtMost(99)}%"
                },
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).fillMaxHeight().background(Zk.colors.border))
}

@Composable
private fun NetworkStat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    val c = Zk.colors
    Row(modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(label, style = Zk.text.fieldLabel.copy(letterSpacing = 0.6.sp), color = c.textMuted, maxLines = 1, softWrap = false)
            Text(value, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, softWrap = false)
        }
    }
}

/** Un movimiento con su ícono en recuadro, cuándo y cuánto. */
@Composable
fun MovementRow(movement: QuanticMovement, onClick: () -> Unit) {
    val c = Zk.colors
    val (icon, label, sign) = when (movement.kind) {
        "sent" -> Triple(Icons.Outlined.CallMade, "Sent", "−")
        "mined" -> Triple(Icons.Outlined.Layers, "Mining reward", "+")
        else -> Triple(Icons.Outlined.CallReceived, "Received", "+")
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconSquare(icon, tint = c.text, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1)
            Text(
                buildString {
                    append(movement.memo.ifEmpty { activityTime(movement.timestamp) })
                    if (movement.confirmations < 3) append(" · ${movement.confirmations} conf.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "$sign${displayZka(movement.amountAtoms)} ZKA",
            style = Zk.text.mono,
            color = c.text,
            maxLines = 1,
        )
    }
}

private fun shortDate(seconds: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(seconds * 1000))

/** Toda la actividad. Se actualiza sola, como el inicio. */
@Composable
fun HistoryScreen(vm: WalletViewModel, onMovement: (String) -> Unit) {
    val state by vm.state.collectAsState()
    HistoryContent(state.history, onMovement)
}

@Composable
internal fun HistoryContent(history: List<QuanticMovement>, onMovement: (String) -> Unit) {
    ZkScreen(title = "Activity") {
        if (history.isEmpty()) {
            EmptyState("No activity yet", sub = "Payments you send and receive appear here.")
        } else {
            Panel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), gap = 0.dp) {
                history.forEachIndexed { i, movement ->
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
    MovementContent(movement, onBack) {
        if (movement != null) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("$EXPLORER/block/${movement.height}")))
        }
    }
}

/** El detalle de un movimiento, sin estado: también lo dibuja la vista de diseño. */
@Composable
internal fun MovementContent(movement: QuanticMovement?, onBack: () -> Unit, onExplorer: () -> Unit) {
    ZkScreen(title = "Movement", onBack = onBack) {
        if (movement == null) {
            EmptyState("Not found", sub = "This movement is not in the wallet anymore.")
        } else {
            Panel(contentPadding = PaddingValues(20.dp), hero = true) {
                Eyebrow(
                    when (movement.kind) {
                        "sent" -> "Sent"
                        "mined" -> "Mining reward"
                        else -> "Received"
                    },
                    dot = true,
                )
                FittedAmount(displayZka(movement.amountAtoms), max = 40.sp, alignment = Alignment.CenterStart)
                if (movement.memo.isNotEmpty()) {
                    Notice(movement.memo, title = "Memo", tone = ZkTone.Info)
                }
                ThinDivider()
                Column {
                    if (movement.kind == "sent") KeyValueRow("Fee", formatZka(movement.feeAtoms) + " ZKA")
                    KeyValueRow("Block", "%,d".format(movement.height))
                    KeyValueRow("Confirmations", "%,d".format(movement.confirmations))
                    KeyValueRow("Date", shortDate(movement.timestamp))
                    CopyableKeyValueRow("Transaction id", movement.txid)
                }
            }
            ZButton(onExplorer, Modifier.fillMaxWidth(), ZButtonVariant.Secondary, ZButtonSize.Large) {
                ZButtonText("Open block in ZokaScan")
            }
            HintText("ZokaScan shows the block, not who paid whom: amounts and addresses stay private.")
        }
    }
}
