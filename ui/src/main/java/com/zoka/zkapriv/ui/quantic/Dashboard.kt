package com.zoka.zkapriv.ui.quantic

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.ui.avatar.ZKAPrivAvatar
import com.zoka.zkapriv.ui.theme.InterFamily
import com.zoka.zkapriv.ui.theme.DashboardGain
import com.zoka.zkapriv.ui.theme.Zk
import java.util.Locale
import kotlin.math.abs

@Composable
fun HomeScreen(vm: WalletViewModel, onWallets: () -> Unit, onSettings: () -> Unit,
    onNetwork: () -> Unit, onSendProgress: () -> Unit) {
    val state by vm.state.collectAsState()
    val send by vm.sendState.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val active by vm.activeWallet.collectAsState()
    val entry = wallets.firstOrNull { it.id == active }
    HomeContent(state, send.running, entry?.name ?: "Wallet", onWallets, onSettings,
        onNetwork, onSendProgress, entry?.publicAccountId.orEmpty())
}

/** Composición de la referencia: cuenta, saldo, curva y anillo con el mosaico plano. */
@Composable
internal fun HomeContent(state: QuanticState, sending: Boolean, walletName: String,
    onWallets: () -> Unit, onSettings: () -> Unit, onNetwork: () -> Unit,
    onSendProgress: () -> Unit, accountId: String = "") {
    val c = Zk.colors
    var hidden by rememberSaveable { mutableStateOf(false) }
    val history = remember(state.balanceAtoms, state.history, state.inSync, state.pendingTxid) {
        if (state.inSync && state.pendingTxid == null) balanceHistory(state.balanceAtoms, state.history) else null
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        MatrixBackdrop(Modifier.fillMaxWidth().height(270.dp))
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { AccountChip(walletName, accountId, onWallets, Modifier) }
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.size(52.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSettings),
                        contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Settings, "Settings", Modifier.size(32.dp), tint = c.text)
                    }
                }
                DashboardBalance(state, hidden, history) { hidden = !hidden }
                BalanceCurve(if (hidden) null else history, Modifier.fillMaxWidth().height(84.dp))
                Spacer(Modifier.height(12.dp))
                NetworkRing(state, accountId, onNetwork)
                if (sending || state.pendingTxid != null) {
                    Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { PendingStrip(sending, onSendProgress) }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun AccountChip(name: String, accountId: String, onClick: () -> Unit, modifier: Modifier) {
    val c = Zk.colors
    Row(modifier.widthIn(max = 270.dp).border(1.dp, c.textSoft, CircleShape).clip(CircleShape)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ZKAPrivAvatar(accountId, Modifier.size(32.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (accountId.isEmpty()) "Loading address…" else accountId.take(7) + "…" + accountId.takeLast(4),
                color = c.textSoft, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1)
        }
        Icon(Icons.Outlined.ExpandMore, "Change wallet", tint = c.text, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DashboardBalance(state: QuanticState, hidden: Boolean, history: BalanceHistory?, onToggle: () -> Unit) {
    val c = Zk.colors
    val amount = when { hidden -> "••••••"; !state.loaded -> "—"; else -> displayZka(state.balanceAtoms) }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (hidden) "Show balance" else "Hide balance" }) {
            val fontScale = LocalDensity.current.fontScale
            val fontSize = (maxWidth.value / ((amount.length + 3) * 0.64f) / fontScale).coerceIn(18f, 36f)
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontSize = fontSize.sp, fontWeight = FontWeight.Bold)) { append(amount) }
                withStyle(SpanStyle(fontSize = (fontSize * 0.72f).sp, fontWeight = FontWeight.Bold)) { append(" ZKA") }
            }, Modifier.fillMaxWidth(), color = c.text, fontFamily = InterFamily, textAlign = TextAlign.Center,
                maxLines = 1, softWrap = false, fontSize = fontSize.sp, lineHeight = (fontSize * 1.15f).sp)
        }
        Spacer(Modifier.height(5.dp))
        val change = history?.change ?: 0
        val sign = if (change >= 0) "+" else "−"
        if (history != null && !hidden) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = if (change < 0) c.danger else if (c.dark) DashboardGain else c.success, fontWeight = FontWeight.SemiBold)) {
                    append(if (change >= 0) "↗ " else "↘ ")
                    history.percent?.let { append(String.format(Locale.US, "%.2f%%  ", abs(it))) }
                    Unit
                }
                withStyle(SpanStyle(color = c.textSoft)) { append("($sign${displayZka(abs(change))} ZKA)") }
            }, fontSize = 15.sp, lineHeight = 19.sp, maxLines = 1)
        }
        Text(if (hidden) "Balance hidden" else if (history != null) "Recent activity" else "Private balance",
            color = c.textMuted, fontSize = 10.sp, lineHeight = 13.sp, modifier = Modifier.padding(top = 3.dp))
        if (!hidden && state.loaded && (state.immatureAtoms > 0 || state.reservedAtoms > 0)) {
            Text(listOfNotNull(
                state.immatureAtoms.takeIf { it > 0 }?.let { "${displayZka(it)} maturing" },
                state.reservedAtoms.takeIf { it > 0 }?.let { "${displayZka(it)} pending" },
            ).joinToString(" · "), color = c.textMuted, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp))
        }
    }
}

/** Curva de los movimientos disponibles; sin puntos inventados ni cotización de mercado. */
@Composable
private fun BalanceCurve(history: BalanceHistory?, modifier: Modifier) {
    val c = Zk.colors
    Canvas(modifier.semantics { contentDescription = if (history == null) "Balance chart unavailable" else "Recent balance chart" }) {
        val values = history?.balances ?: return@Canvas
        val low = values.min().toDouble()
        val spread = (values.max().toDouble() - low).coerceAtLeast(1.0)
        val points = values.mapIndexed { i, value ->
            Offset(i.toFloat() / (values.size - 1) * size.width,
                size.height * (0.87f - ((value - low) / spread).toFloat() * 0.72f))
        }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.zipWithNext().forEach { (a, b) ->
                val mid = (a.x + b.x) / 2
                cubicTo(mid, a.y, mid, b.y, b.x, b.y)
            }
        }
        val area = Path().apply { addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(c.text.copy(alpha = 0.14f), c.text.copy(alpha = 0f))))
        for (width in listOf(12f, 10f, 8f, 6f, 4f)) drawPath(line, c.text.copy(alpha = 0.012f), style = Stroke(width.dp.toPx(), cap = StrokeCap.Round))
        drawPath(line, c.text, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** El anillo es un marco de red, no un gráfico de proporciones entre unidades incompatibles. */
@Composable
private fun NetworkRing(state: QuanticState, accountId: String, onNetwork: () -> Unit) {
    val c = Zk.colors
    BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 540.dp).aspectRatio(1.2f)) {
        val diameter = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.5f, size.height * 0.50f)
            val radius = size.width * 0.315f
            val stroke = size.width * 0.060f
            val box = Size(radius * 2, radius * 2)
            val top = center - Offset(radius, radius)
            for ((start, sweep) in listOf(225f to 90f, 330f to 58f, 42f to 30f, 112f to 99f)) {
                drawArc(if (start == 42f) c.text.copy(alpha = 0.18f) else c.text,
                    start, sweep, false, top, box, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            drawCircle(c.text.copy(alpha = 0.05f), radius * 0.69f, center)
            drawCircle(c.text.copy(alpha = 0.28f), radius * 0.67f, center, style = Stroke(1.dp.toPx()))
            listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (x, y) ->
                val dot = center + Offset(x * size.width * 0.263f, y * size.width * 0.253f)
                val end = dot + Offset(x * size.width * 0.036f, y * size.width * 0.026f)
                drawLine(c.textSoft, dot, end, 1.dp.toPx())
                drawCircle(c.textSoft, 2.dp.toPx(), dot)
            }
        }
        Box(Modifier.align(Alignment.Center).size(diameter * 0.40f).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onNetwork)
            .semantics { contentDescription = "Network details" }, contentAlignment = Alignment.Center) {
            ZKAPrivAvatar(accountId, Modifier.size(80.dp).testTag("home-avatar"))
        }
        Metric("Height", (if (state.tip > 0) state.tip else state.height).takeIf { it > 0 }
            ?.let { String.format(Locale.US, "%,d", it) } ?: "—",
            Modifier.align(Alignment.TopStart).padding(start = 25.dp), false)
        Metric("Hashrate", if (state.torEnabled) "—" else displayHashrate(state.hashrateHps),
            Modifier.align(Alignment.TopEnd).padding(end = 25.dp), true)
        Metric("Sync", when { state.tip <= 0 -> "—"; state.inSync -> "100%"; else -> "${(state.progress * 100).toInt().coerceAtMost(99)}%" },
            Modifier.align(Alignment.BottomStart).padding(start = 25.dp), false)
        Metric("Difficulty", if (state.torEnabled) "—" else state.difficultyBits?.let { "$it bits" } ?: "—",
            Modifier.align(Alignment.BottomEnd).padding(end = 25.dp), true)
    }
    Text(when { state.error != null -> "Out of date · retrying"; state.lastSyncMillis == 0L -> "Connecting…";
        !state.inSync -> "Syncing…"; else -> "Quantic Testnet · Connected" },
        Modifier.fillMaxWidth().padding(top = 16.dp).clickable(onClick = onNetwork),
        color = if (state.error != null) c.warning else c.textMuted, textAlign = TextAlign.Center, fontSize = 10.sp, lineHeight = 13.sp)
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier, right: Boolean) {
    Column(modifier, horizontalAlignment = if (right) Alignment.End else Alignment.Start) {
        Text(label, color = Zk.colors.textSoft, fontSize = 13.sp, lineHeight = 17.sp)
        Text(value, color = Zk.colors.text, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Trama estática, decorativa y tenue; no contiene información de la cuenta. */
@Composable
private fun MatrixBackdrop(modifier: Modifier) {
    val color = Zk.colors.text
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE } }
    Canvas(modifier) {
        paint.textSize = 7.dp.toPx()
        val stepX = 15.dp.toPx()
        val stepY = 12.dp.toPx()
        drawIntoCanvas { canvas ->
            for (row in 0..(size.height / stepY).toInt()) {
                for (col in 0..(size.width / stepX).toInt()) {
                    paint.color = color.copy(alpha = 0.045f * (1f - row * stepY / size.height).coerceIn(0f, 1f)).toArgb()
                    canvas.nativeCanvas.drawText(if ((col * 7 + row * 13) % 5 < 2) "1" else "0",
                        col * stepX + 5.dp.toPx(), row * stepY, paint)
                }
            }
        }
    }
}
