package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.zoka.zkapriv.ui.avatar.AvatarMosaic
import com.zoka.zkapriv.ui.avatar.ZKAPrivAvatar
import com.zoka.zkapriv.ui.theme.DisplayFamily
import com.zoka.zkapriv.ui.theme.InterFamily
import com.zoka.zkapriv.ui.theme.MonoFamily
import com.zoka.zkapriv.ui.theme.Zk
import java.util.Locale

/** La red de esta compilación: el título del inicio. */
private const val NETWORK_TITLE = "QUANTIC"
private const val NETWORK_SUBTITLE = "v3 Testnet"

@Composable
fun HomeScreen(vm: WalletViewModel, onWallets: () -> Unit, onSettings: () -> Unit, onSendProgress: () -> Unit) {
    val state by vm.state.collectAsState()
    val send by vm.sendState.collectAsState()
    val wallets by vm.wallets.collectAsState()
    val active by vm.activeWallet.collectAsState()
    val addresses by vm.addresses.collectAsState()
    val entry = wallets.firstOrNull { it.id == active }
    val birth = remember(active) { vm.birth() }
    HomeContent(state, send.running, entry?.name ?: "Wallet", onWallets, onSettings, onSendProgress,
        entry?.publicAccountId.orEmpty(), birth, addresses.size.coerceAtLeast(1))
}

/**
 * El inicio: el título de la red, la cuenta en una línea fina, el saldo grande
 * y fino, y debajo la credencial de la cuenta con su ID único (el mosaico).
 * Sin curva: no hay precio que seguir. El anillo de la red está en la barra.
 */
@Composable
internal fun HomeContent(state: QuanticState, sending: Boolean, walletName: String,
    onWallets: () -> Unit, onSettings: () -> Unit, onSendProgress: () -> Unit, accountId: String = "",
    birth: Int = 0, addressCount: Int = 1) {
    val c = Zk.colors
    var hidden by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(buildAnnotatedString {
                    append("ZOKA ")
                    withStyle(SpanStyle(color = c.accent)) { append(NETWORK_TITLE) }
                }, color = c.text, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    lineHeight = 21.sp, letterSpacing = 3.sp, maxLines = 1, softWrap = false)
                Text(NETWORK_SUBTITLE.uppercase(Locale.US), color = c.textMuted, fontSize = 10.sp, lineHeight = 13.sp,
                    letterSpacing = 2.4.sp, maxLines = 1)
            }
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onSettings),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Settings, "Settings", Modifier.size(26.dp), tint = c.text)
            }
        }
        AccountChip(walletName, accountId, onWallets, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
        Spacer(Modifier.weight(1f))
        DashboardBalance(state, hidden) { hidden = !hidden }
        Spacer(Modifier.height(28.dp))
        AccountCredential(walletName, accountId, addressCount, birth, Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.weight(1.15f))
        if (sending || state.pendingTxid != null) {
            Box(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) { PendingStrip(sending, onSendProgress) }
        }
    }
}

/** La cuenta en una sola línea fina y a lo ancho: ícono, nombre, dirección corta y la flecha. */
@Composable
private fun AccountChip(name: String, accountId: String, onClick: () -> Unit, modifier: Modifier) {
    val c = Zk.colors
    Row(modifier.fillMaxWidth().border(1.dp, c.textSoft.copy(alpha = 0.55f), CircleShape).clip(CircleShape)
        .clickable(role = Role.Button, onClick = onClick).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        .testTag("account-chip"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DashboardNavIcon("home", c.text, Modifier.size(16.dp))
        Text(name, color = c.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 16.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(shortAccount(accountId), color = c.textSoft, fontFamily = MonoFamily, fontSize = 11.5.sp, lineHeight = 16.sp,
            maxLines = 1, softWrap = false, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Icon(Icons.Outlined.ExpandMore, "Change wallet", tint = c.text, modifier = Modifier.size(18.dp))
    }
}

private fun shortAccount(accountId: String) =
    if (accountId.isEmpty()) "Loading…" else accountId.take(7) + "…" + accountId.takeLast(4)

/**
 * El logo de ZKAPriv en lugar de la palabra «ZKA»: chico y en gris, para que
 * el número sea lo que se ve. El lugar reservado deja aire a cada lado.
 */
private fun zkaLogo(height: TextUnit, tint: Color) = InlineTextContent(
    Placeholder(width = (height.value * (600f / 740f) + 0.32f).em, height = height,
        placeholderVerticalAlign = PlaceholderVerticalAlign.AboveBaseline),
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.zkapriv_logo_mark), "ZKA", Modifier.fillMaxHeight().aspectRatio(600f / 740f),
            colorFilter = ColorFilter.tint(tint))
    }
}

/** Ancho aproximado en «em» de un monto en Montserrat Light, con el logo al final. */
private fun amountEms(text: String): Float =
    text.sumOf { ch -> when { ch.isDigit() -> 0.66; ch == '•' -> 0.42; ch == '—' -> 0.9; else -> 0.27 } }.toFloat() + 0.7f

@Composable
private fun DashboardBalance(state: QuanticState, hidden: Boolean, onToggle: () -> Unit) {
    val c = Zk.colors
    val amount = when { hidden -> "••••••"; !state.loaded -> "—"; else -> displayZka(state.balanceAtoms) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 18.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = if (hidden) "Show balance" else "Hide balance" }) {
            val fontScale = LocalDensity.current.fontScale
            val fontSize = (maxWidth.value / amountEms(amount) / fontScale).coerceIn(22f, 60f)
            Text(buildAnnotatedString {
                append(amount)
                appendInlineContent("zka", "ZKA")
            }, Modifier.fillMaxWidth().testTag("home-balance"), color = c.text, fontFamily = DisplayFamily,
                fontWeight = FontWeight.Light, textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                fontSize = fontSize.sp, lineHeight = (fontSize * 1.15f).sp, letterSpacing = (-0.5).sp,
                inlineContent = mapOf("zka" to zkaLogo(0.4.em, c.textSoft)))
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

/** La credencial es negra en los dos temas, como los botones. */
private val CardInk = listOf(Color(0xFF18181B), Color(0xFF050506))
private val CardText = Color(0xFFFAFAFA)
private val CardSoft = Color(0xFFB4B4BA)
private val CardMuted = Color(0xFF7C7C84)

/**
 * La credencial de la cuenta: su ID único (el mosaico y su código, sacados de
 * la dirección pública) y unos datos que la identifican. Nada secreto: todo
 * sale de lo público o de lo que la app guarda sin cifrar sobre la cuenta.
 */
@Composable
private fun AccountCredential(name: String, accountId: String, addresses: Int, birth: Int, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    // El código largo del Zoka ID (v2): los dos primeros grupos son el de la 4.1.0.
    val code = remember(accountId) { AvatarMosaic.codeV2(accountId) }
    Column(modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(CardInk))
        .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.05f))), shape)
        .padding(horizontal = 18.dp, vertical = 16.dp).testTag("account-credential")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel("ZOKA ID", Modifier.weight(1f))
            Image(painterResource(R.drawable.zkapriv_logo_mark), null, Modifier.height(14.dp).aspectRatio(600f / 740f),
                colorFilter = ColorFilter.tint(CardMuted))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ZKAPrivAvatar(accountId, Modifier.size(64.dp).testTag("credential-avatar"))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = CardText, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 17.sp,
                    lineHeight = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                CardLabel("UNIQUE ID")
                Text(code ?: "—", color = CardText, fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 17.sp,
                    letterSpacing = 0.4.sp, maxLines = 1, softWrap = false, modifier = Modifier.testTag("unique-id"))
            }
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CardField("ADDRESS", shortAccount(accountId), Modifier.weight(1.25f))
            CardField("ADDRESSES", "$addresses", Modifier.weight(0.95f))
            CardField("SINCE BLOCK", if (birth > 0) String.format(Locale.US, "%,d", birth) else "—", Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun CardLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = CardMuted, fontFamily = InterFamily, fontWeight = FontWeight.Medium, fontSize = 9.sp,
        lineHeight = 12.sp, letterSpacing = 1.1.sp, maxLines = 1, softWrap = false)
}

@Composable
private fun CardField(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        CardLabel(label)
        Spacer(Modifier.height(3.dp))
        Text(value, color = CardSoft, fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1,
            softWrap = false, overflow = TextOverflow.Ellipsis)
    }
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
