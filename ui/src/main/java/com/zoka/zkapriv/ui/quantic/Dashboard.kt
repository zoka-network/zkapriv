package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.zoka.zkapriv.network.quantic.QuanticState
import com.zoka.zkapriv.ui.R
import com.zoka.zkapriv.ui.avatar.ZKAPrivAvatar
import com.zoka.zkapriv.ui.theme.DashboardGain
import com.zoka.zkapriv.ui.theme.DisplayFamily
import com.zoka.zkapriv.ui.theme.Zk
import java.util.Locale
import kotlin.math.abs

/** La red de esta compilación, como la muestra el selector de cuenta. */
private const val NETWORK_LINE_1 = "ZOKA QUANTIC"
private const val NETWORK_LINE_2 = "v3 Testnet"

@Composable
fun HomeScreen(vm: WalletViewModel, onWallets: () -> Unit, onSettings: () -> Unit, onSendProgress: () -> Unit) {
    val state by vm.state.collectAsState()
    val send by vm.sendState.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val active by vm.activeWallet.collectAsState()
    val entry = wallets.firstOrNull { it.id == active }
    HomeContent(state, send.running, entry?.name ?: "Wallet", onWallets, onSettings, onSendProgress,
        entry?.publicAccountId.orEmpty())
}

/**
 * El inicio, limpio: la cuenta arriba (a lo largo, con la red), el saldo con
 * el logo en lugar de «ZKA» y la curva de los movimientos con un degradé de
 * la línea hacia arriba. Los datos de la red (el anillo) se abren en una hoja
 * desde la barra de abajo.
 */
@Composable
internal fun HomeContent(state: QuanticState, sending: Boolean, walletName: String,
    onWallets: () -> Unit, onSettings: () -> Unit, onSendProgress: () -> Unit, accountId: String = "") {
    val c = Zk.colors
    var hidden by rememberSaveable { mutableStateOf(false) }
    val history = remember(state.loaded, state.balanceAtoms, state.history) {
        if (state.loaded) balanceHistory(state.balanceAtoms, state.history) else null
    }
    Box(Modifier.fillMaxSize().background(c.background)) {
        // Sin datos o con el saldo oculto, la línea queda plana: nunca se inventa una forma.
        BalanceBackdrop(if (hidden) null else history?.balances, Modifier.fillMaxSize().testTag("home-curve"))
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AccountChip(walletName, accountId, onWallets, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSettings),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Settings, "Settings", Modifier.size(28.dp), tint = c.text)
                }
            }
            // El saldo queda sobre la curva, que la dibuja el fondo (ver CURVE_TOP).
            Spacer(Modifier.weight(0.42f))
            DashboardBalance(state, hidden, history) { hidden = !hidden }
            Spacer(Modifier.weight(1.58f))
            if (sending || state.pendingTxid != null) {
                Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { PendingStrip(sending, onSendProgress) }
            }
        }
    }
}

/**
 * La cuenta, fina y a lo largo: ícono de billetera, nombre y dirección corta;
 * a la derecha, la red («ZOKA QUANTIC · v3 Testnet») y la flecha.
 */
@Composable
private fun AccountChip(name: String, accountId: String, onClick: () -> Unit, modifier: Modifier) {
    val c = Zk.colors
    Row(modifier.border(1.dp, c.textSoft.copy(alpha = 0.7f), CircleShape).clip(CircleShape)
        .clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 10.dp, top = 4.dp, bottom = 4.dp)
        .testTag("account-chip"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardNavIcon("home", c.text, Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, lineHeight = 15.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (accountId.isEmpty()) "Loading address…" else accountId.take(7) + "…" + accountId.takeLast(4),
                color = c.textSoft, fontSize = 10.5.sp, lineHeight = 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(buildAnnotatedString {
                append(NETWORK_LINE_1.substringBefore(' ') + " ")
                withStyle(SpanStyle(color = c.accent)) { append(NETWORK_LINE_1.substringAfter(' ')) }
            }, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 9.sp, lineHeight = 12.sp,
                letterSpacing = 0.9.sp, maxLines = 1, softWrap = false)
            Text(NETWORK_LINE_2, color = c.textSoft, fontSize = 10.5.sp, lineHeight = 12.sp, maxLines = 1, softWrap = false)
        }
        Icon(Icons.Outlined.ExpandMore, "Change wallet", tint = c.text, modifier = Modifier.size(18.dp))
    }
}

/**
 * El logo de ZKAPriv en lugar de la palabra «ZKA», del alto de los números.
 * El lugar reservado es un poco más ancho que el logo: deja aire a cada lado.
 */
private fun zkaLogo(height: TextUnit) = InlineTextContent(
    Placeholder(width = (height.value * (600f / 740f + 0.36f)).em, height = height,
        placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline),
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.zkapriv_logo_mark), "ZKA", Modifier.fillMaxHeight().aspectRatio(600f / 740f))
    }
}

/** Ancho aproximado en «em» de un monto en Montserrat Bold, con el logo al final. */
private fun amountEms(text: String): Float =
    text.sumOf { ch -> when { ch.isDigit() -> 0.70; ch == '•' -> 0.45; ch == '—' -> 0.9; else -> 0.30 } }.toFloat() + 1.0f

@Composable
private fun DashboardBalance(state: QuanticState, hidden: Boolean, history: BalanceHistory?, onToggle: () -> Unit) {
    val c = Zk.colors
    val amount = when { hidden -> "••••••"; !state.loaded -> "—"; else -> displayZka(state.balanceAtoms) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (hidden) "Show balance" else "Hide balance" }) {
            val fontScale = LocalDensity.current.fontScale
            val fontSize = (maxWidth.value / amountEms(amount) / fontScale).coerceIn(20f, 56f)
            Text(buildAnnotatedString {
                append(amount)
                appendInlineContent("zka", "ZKA")
            }, Modifier.fillMaxWidth(), color = c.text, fontFamily = DisplayFamily, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 1, softWrap = false, fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.15f).sp, letterSpacing = (-0.5).sp,
                inlineContent = mapOf("zka" to zkaLogo(0.72.em)))
        }
        // La variación solo con movimientos reales: sin ellos no hay nada que comparar.
        if (history != null && history.balances.size > 1 && !hidden) {
            val change = history.change
            val sign = if (change >= 0) "+" else "−"
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = if (change < 0) c.danger else if (c.dark) DashboardGain else c.success)) {
                    append(if (change >= 0) "↗ " else "↘ ")
                    history.percent?.let { append(String.format(Locale.US, "%.2f%%  ", abs(it))) }
                    Unit
                }
                withStyle(SpanStyle(color = c.textSoft)) {
                    append("($sign${displayZka(abs(change))}")
                    appendInlineContent("zka", "ZKA")
                    append(")")
                }
            }, fontFamily = DisplayFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp,
                maxLines = 1, modifier = Modifier.padding(top = 6.dp), inlineContent = mapOf("zka" to zkaLogo(0.72.em)))
        }
        if (!hidden && state.loaded && (state.immatureAtoms > 0 || state.reservedAtoms > 0)) {
            Text(listOfNotNull(
                state.immatureAtoms.takeIf { it > 0 }?.let { "${displayZka(it)} maturing" },
                state.reservedAtoms.takeIf { it > 0 }?.let { "${displayZka(it)} pending" },
            ).joinToString(" · "), color = c.textMuted, fontSize = 10.sp, lineHeight = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp))
        }
    }
}

/** La franja de la curva, en fracciones del alto del inicio. */
private const val CURVE_TOP = 0.46f
private const val CURVE_BOTTOM = 0.76f

/** Línea de los movimientos y lo que queda encima, donde va el degradé. */
private class CurveGeometry(val line: Path, val above: Path, val lowest: Float)

private fun curveGeometry(values: List<Long>?, size: Size): CurveGeometry {
    val top = size.height * CURVE_TOP
    val bottom = size.height * CURVE_BOTTOM
    val points = if (values == null || values.size < 2 || values.max() == values.min()) {
        // Sin movimientos (o todos iguales): plana, en la parte baja de la franja.
        val y = bottom - (bottom - top) * 0.3f
        listOf(Offset(-2f, y), Offset(size.width + 2f, y))
    } else {
        val low = values.min().toDouble()
        val spread = values.max().toDouble() - low
        values.mapIndexed { i, value ->
            Offset(-2f + i.toFloat() / (values.size - 1) * (size.width + 4f),
                bottom - ((value - low) / spread).toFloat() * (bottom - top))
        }
    }
    val line = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.zipWithNext().forEach { (a, b) ->
            val mid = (a.x + b.x) / 2
            cubicTo(mid, a.y, mid, b.y, b.x, b.y)
        }
    }
    val above = Path().apply { addPath(line); lineTo(size.width + 2f, -2f); lineTo(-2f, -2f); close() }
    return CurveGeometry(line, above, points.maxOf { it.y })
}

/** El fondo del inicio: la curva con su resplandor y el degradé de la línea hacia arriba. Quieto. */
@Composable
private fun BalanceBackdrop(values: List<Long>?, modifier: Modifier) {
    val ink = Zk.colors.text
    Box(modifier.semantics { contentDescription = if (values == null) "Balance chart" else "Recent balance chart" }
        .drawWithCache {
            val geo = curveGeometry(values, size)
            val wash = Brush.verticalGradient(listOf(ink.copy(alpha = 0f), ink.copy(alpha = 0.035f), ink.copy(alpha = 0.13f)),
                startY = size.height * (CURVE_TOP - 0.42f), endY = geo.lowest)
            onDrawBehind {
                clipPath(geo.above) {
                    drawRect(wash)
                    for ((width, alpha) in listOf(46.dp to 0.012f, 36.dp to 0.012f, 27.dp to 0.014f, 19.dp to 0.016f, 12.dp to 0.02f, 6.dp to 0.03f)) {
                        drawPath(geo.line, ink.copy(alpha = alpha), style = Stroke(width.toPx(), cap = StrokeCap.Round))
                    }
                }
                for ((width, alpha) in listOf(7.dp to 0.06f, 3.5.dp to 0.14f)) {
                    drawPath(geo.line, ink.copy(alpha = alpha), style = Stroke(width.toPx(), cap = StrokeCap.Round))
                }
                drawPath(geo.line, ink, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            }
        })
}

// ── La red, en su hoja ───────────────────────────────────────────────────────

/** El anillo de la red con sus cuatro datos y, abajo, el estado. Va en una hoja. */
@Composable
internal fun NetworkPanel(state: QuanticState, accountId: String, onDetails: () -> Unit) {
    val c = Zk.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        NetworkRing(state, accountId)
        Text(when { state.error != null -> "Out of date · retrying"; state.lastSyncMillis == 0L -> "Connecting…";
            !state.inSync -> "Syncing…"; else -> "Quantic Testnet · Connected" },
            Modifier.padding(top = 8.dp), color = if (state.error != null) c.warning else c.textMuted,
            textAlign = TextAlign.Center, fontSize = 11.sp, lineHeight = 14.sp)
        Text("Network details", Modifier.padding(top = 6.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onDetails)
            .padding(horizontal = 14.dp, vertical = 10.dp), color = c.accentSoft, fontSize = 13.sp, lineHeight = 16.sp)
    }
}

/**
 * El anillo es un marco, no un gráfico de proporciones. Cada dato queda en su
 * esquina, afuera del anillo, y la guía sale de su borde hacia el anillo sin
 * cruzar las letras.
 */
@Composable
private fun NetworkRing(state: QuanticState, accountId: String) {
    val c = Zk.colors
    BoxWithConstraints(Modifier.fillMaxWidth().widthIn(max = 440.dp)) {
        val w = maxWidth
        val h = w * 0.92f
        val outer = w * 0.30f
        val stroke = w * 0.055f
        val reach = (outer + w * 0.035f) * 0.7071f // del centro al punto, en cada eje
        val tail = w * 0.035f
        val corner = reach + tail // del centro al extremo de la guía, en cada eje
        Box(Modifier.fillMaxWidth().height(h)) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2, size.height / 2)
                val r = (outer - stroke / 2).toPx()
                val box = Size(r * 2, r * 2)
                val topLeft = center - Offset(r, r)
                for ((start, sweep) in listOf(225f to 90f, 330f to 58f, 42f to 30f, 112f to 99f)) {
                    drawArc(if (start == 42f) c.text.copy(alpha = 0.18f) else c.text,
                        start, sweep, false, topLeft, box, style = Stroke(stroke.toPx(), cap = StrokeCap.Round))
                }
                val inner = (outer - stroke).toPx() - w.toPx() * 0.05f
                drawCircle(c.text.copy(alpha = 0.05f), inner, center)
                drawCircle(c.text.copy(alpha = 0.28f), inner, center, style = Stroke(1.dp.toPx()))
                listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).forEach { (x, y) ->
                    val dot = center + Offset(x * reach.toPx(), y * reach.toPx())
                    val end = dot + Offset(x * tail.toPx(), y * tail.toPx())
                    drawLine(c.textSoft, dot, end, 1.dp.toPx())
                    drawCircle(c.textSoft, 2.dp.toPx(), dot)
                }
            }
            Box(Modifier.align(Alignment.Center).size(w * 0.30f), contentAlignment = Alignment.Center) {
                ZKAPrivAvatar(accountId, Modifier.size(w * 0.19f).testTag("home-avatar"))
            }
            val gap = 4.dp
            val labelTop = h / 2 - corner - gap
            val labelBottom = h / 2 + corner + gap
            Metric("Height", (if (state.tip > 0) state.tip else state.height).takeIf { it > 0 }
                ?.let { String.format(Locale.US, "%,d", it) } ?: "—", false, top = null, bottom = labelTop)
            Metric("Hashrate", if (state.torEnabled) "—" else displayHashrate(state.hashrateHps), true,
                top = null, bottom = labelTop)
            Metric("Sync", when { state.tip <= 0 -> "—"; state.inSync -> "100%"
                else -> "${(state.progress * 100).toInt().coerceAtMost(99)}%" }, false, top = labelBottom, bottom = null)
            Metric("Difficulty", if (state.torEnabled) "—" else state.difficultyBits?.let { "$it bits" } ?: "—", true,
                top = labelBottom, bottom = null)
        }
    }
}

/** Un dato del anillo: arriba se apoya sobre la guía (`bottom`), abajo cuelga de ella (`top`). */
@Composable
private fun BoxScope.Metric(label: String, value: String, right: Boolean, top: Dp?, bottom: Dp?) {
    val placement = if (top != null) Modifier.align(if (right) Alignment.TopEnd else Alignment.TopStart).padding(top = top)
    else Modifier.align(Alignment.TopStart).fillMaxWidth().height(bottom!!)
    Box(placement.padding(horizontal = 22.dp)) {
        Column(Modifier.align(if (right) Alignment.BottomEnd else Alignment.BottomStart),
            horizontalAlignment = if (right) Alignment.End else Alignment.Start) {
            Text(label, color = Zk.colors.textSoft, fontSize = 12.sp, lineHeight = 15.sp)
            Text(value, color = Zk.colors.text, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}
