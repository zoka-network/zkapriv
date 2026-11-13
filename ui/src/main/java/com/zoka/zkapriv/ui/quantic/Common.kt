package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.GlassIconButton
import com.zoka.zkapriv.ui.components.IconSquare
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZLabel
import com.zoka.zkapriv.ui.components.ZToggle
import com.zoka.zkapriv.ui.components.zkCapsule
import com.zoka.zkapriv.ui.components.zkPulseAlpha
import com.zoka.zkapriv.ui.components.zkShake
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay

/**
 * Pantalla de la app: fondo del tema también bajo las barras del sistema,
 * barra superior con volver y título, y contenido que se desplaza. Las
 * pestañas (sin volver) llevan el título grande. `backdrop` va detrás de todo:
 * el arco de luz de la bienvenida y del bloqueo.
 */
@Composable
fun ZkScreen(
    title: String?,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    scroll: Boolean = true,
    bottom: @Composable (() -> Unit)? = null,
    backdrop: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Zk.colors.background)) {
        backdrop?.invoke()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .let { if (LocalWalletTabs.current) it else it.navigationBarsPadding() }
                .imePadding(),
        ) {
            if (title != null || onBack != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp)
                        .padding(horizontal = Zk.spacing.screen)
                        .padding(top = if (onBack == null) 8.dp else 0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (onBack != null) GlassIconButton(Icons.Outlined.ArrowBack, "Back", onBack)
                    Text(
                        title.orEmpty(),
                        style = if (onBack == null) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleLarge,
                        color = Zk.colors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    actions()
                }
            }
            val body = Modifier
                .weight(1f)
                .fillMaxWidth()
                .let { if (scroll) it.verticalScroll(rememberScrollState()) else it }
                .padding(horizontal = Zk.spacing.screen, vertical = 8.dp)
            Column(body, verticalArrangement = Arrangement.spacedBy(Zk.spacing.gap), content = content)
            if (bottom != null) {
                Box(Modifier.fillMaxWidth().padding(horizontal = Zk.spacing.screen, vertical = 12.dp)) { bottom() }
            }
        }
    }
}

// ── Filas de ajustes ─────────────────────────────────────────────────────────

/** Un grupo de filas: su etiqueta arriba y las filas en una tarjeta de vidrio. */
@Composable
internal fun SettingsGroup(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZLabel(label, Modifier.padding(start = 4.dp))
        Panel(contentPadding = PaddingValues(0.dp), gap = 0.dp, content = content)
    }
}

/** El divisor entre las filas de un grupo. */
@Composable
internal fun RowDivider() = ThinDivider(Modifier.padding(horizontal = 14.dp))

/** Una fila que lleva a otra pantalla (flecha) o afuera de la app (`external`). */
@Composable
internal fun SettingRow(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    value: String? = null,
    sub: String? = null,
    external: Boolean = false,
    danger: Boolean = false,
) {
    val c = Zk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconSquare(icon, tint = if (danger) c.danger else c.textSoft, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (danger) c.danger else c.text)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        if (value != null) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 140.dp),
            )
        }
        Icon(
            if (external) Icons.Outlined.NorthEast else Icons.Outlined.ChevronRight,
            if (external) "Opens in the browser" else null,
            tint = c.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Una fila que solo muestra un dato. */
@Composable
internal fun ValueRow(title: String, value: String, icon: ImageVector? = null) {
    val c = Zk.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconSquare(icon, size = 36.dp)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
        Text(value, style = Zk.text.mono, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Una fila con interruptor: se toca entera. */
@Composable
internal fun SwitchRow(
    title: String,
    sub: String?,
    checked: Boolean,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    val c = Zk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) IconSquare(icon, size = 36.dp)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.text)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
        ZToggle(checked, onCheckedChange = null, enabled = enabled)
    }
}

// ── PIN ──────────────────────────────────────────────────────────────────────

/** Seis puntos del PIN: rosa los escritos, un aro los que faltan. */
@Composable
fun PinDots(filled: Int, error: Boolean = false, modifier: Modifier = Modifier) {
    val c = Zk.colors
    Row(
        modifier.semantics { contentDescription = "$filled of ${QuanticVault.PIN_LENGTH} digits" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(QuanticVault.PIN_LENGTH) { i ->
            val fill = when {
                error -> c.danger
                i < filled -> c.accent
                else -> null
            }
            Box(
                Modifier
                    .size(13.dp)
                    .let {
                        if (fill != null) it.background(fill, CircleShape)
                        else it.border(1.dp, c.borderStrong, CircleShape)
                    },
            )
        }
    }
}

/**
 * Teclado numérico del PIN, con teclas redondas de vidrio. `onBiometric`
 * agrega la huella abajo a la izquierda. Las teclas se achican solas si la
 * pantalla es angosta o baja.
 */
@Composable
fun PinPad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val rows = listOf("123", "456", "789")
    val gap = 18.dp
    BoxWithConstraints(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f), contentAlignment = Alignment.Center) {
        val byWidth = (maxWidth - gap * 2) / 3
        // Tres separaciones de 12 dp y lugar para lo que va debajo.
        val byHeight = if (constraints.hasBoundedHeight) (maxHeight - 100.dp) / 4 else 72.dp
        val key = minOf(byWidth, byHeight, 72.dp).coerceAtLeast(52.dp)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { digit -> PinKey(key, enabled, { onDigit(digit) }) { PinKeyText(digit.toString()) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                if (onBiometric != null) {
                    PinKey(key, enabled, onBiometric, "Use biometrics", plain = true) {
                        Icon(Icons.Outlined.Fingerprint, null, tint = Zk.colors.accent, modifier = Modifier.size(28.dp))
                    }
                } else {
                    Spacer(Modifier.size(key))
                }
                PinKey(key, enabled, { onDigit('0') }) { PinKeyText("0") }
                PinKey(key, enabled, onDelete, "Delete", plain = true) {
                    Icon(Icons.Outlined.Backspace, null, tint = Zk.colors.textMuted, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Una tecla redonda; `plain` no lleva borde (la huella y borrar). */
@Composable
private fun PinKey(
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
    plain: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .size(size)
            .then(
                when {
                    !plain -> Modifier.zkCapsule(pressed = pressed)
                    pressed -> Modifier.background(Zk.colors.surface2, CircleShape)
                    else -> Modifier
                },
            )
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PinKeyText(text: String) {
    Text(text, style = Zk.text.key, color = Zk.colors.text)
}

/**
 * El armado de toda pantalla que pide un PIN: título, bajada, los seis puntos,
 * un mensaje y el teclado. Va dentro de un [ZkScreen] sin scroll. `header` se
 * dibuja sobre el título y `footer` bajo el teclado; los puntos tiemblan cada
 * vez que `shake` cambia.
 */
@Composable
fun ColumnScope.PinGate(
    title: String,
    filled: Int,
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    subtitle: String? = null,
    message: String? = null,
    isError: Boolean = false,
    shake: Int = 0,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    val c = Zk.colors
    Spacer(Modifier.weight(0.6f))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (header != null) {
            header()
            VSpace(16)
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = c.text, textAlign = TextAlign.Center)
        if (subtitle != null) {
            VSpace(8)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 300.dp),
            )
        }
        VSpace(26)
        PinDots(filled, error = isError && filled == 0, modifier = Modifier.zkShake(shake))
        VSpace(12)
        // Alto fijo: el teclado no salta cuando aparece o se va el mensaje.
        Box(Modifier.heightIn(min = 36.dp), contentAlignment = Alignment.TopCenter) {
            if (message != null) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) c.danger else c.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    Spacer(Modifier.weight(0.4f))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PinPad(onDigit, onDelete, onBiometric = onBiometric, enabled = enabled)
        if (footer != null) {
            VSpace(8)
            footer()
        }
    }
    VSpace(8)
}

/**
 * Pide el PIN de la app y avisa cuando es el correcto. Lleva lo escrito, el
 * mensaje de error y la espera después de varios fallos seguidos (la cuenta
 * [QuanticVault]): mientras dura, el teclado queda apagado.
 */
@Composable
fun ColumnScope.PinPrompt(
    title: String,
    check: (String) -> QuanticVault.PinCheck,
    waitMillis: () -> Long,
    onOk: () -> Unit,
    subtitle: String? = null,
    onBiometric: (() -> Unit)? = null,
    header: @Composable (() -> Unit)? = null,
    footer: @Composable (() -> Unit)? = null,
) {
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var misses by remember { mutableIntStateOf(0) }
    var wait by remember { mutableLongStateOf(waitMillis()) }
    LaunchedEffect(wait) {
        if (wait > 0) {
            delay(1000)
            wait = waitMillis()
        }
    }
    LaunchedEffect(entry) {
        if (entry.length < QuanticVault.PIN_LENGTH) return@LaunchedEffect
        when (val result = check(entry)) {
            QuanticVault.PinCheck.Ok -> onOk()
            is QuanticVault.PinCheck.Wrong -> {
                message = if (result.attemptsLeft > 0) "Wrong PIN. ${result.attemptsLeft} tries before a wait." else "Wrong PIN."
                misses++
            }
            is QuanticVault.PinCheck.Wait -> {
                message = null
                misses++
                wait = result.millis
            }
        }
        entry = ""
    }
    PinGate(
        title = title,
        filled = entry.length,
        onDigit = { digit ->
            message = null
            if (entry.length < QuanticVault.PIN_LENGTH) entry += digit
        },
        onDelete = { entry = entry.dropLast(1) },
        subtitle = subtitle,
        message = if (wait > 0) "Too many wrong PINs. Try again in ${(wait + 999) / 1000} s." else message,
        isError = wait > 0 || message != null,
        shake = misses,
        enabled = wait == 0L,
        onBiometric = onBiometric,
        header = header,
        footer = footer,
    )
}

// ── Montos y estados ─────────────────────────────────────────────────────────

/**
 * Un monto con "ZKA" al lado, del tamaño que entre en el ancho: a 360 dp con
 * la letra del sistema al 110 % una cifra larga no se corta.
 */
@Composable
internal fun FittedAmount(
    amount: String,
    max: TextUnit,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.Center,
) {
    val c = Zk.colors
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = alignment) {
        // Una cifra ocupa ~0,62 de su tamaño, un separador la mitad, y "ZKA"
        // con su espacio, lo que 1,3 cifras.
        val density = LocalDensity.current
        val separators = amount.count { it == ',' || it == '.' }
        val units = (amount.length - separators) * 0.62f + separators * 0.32f + 1.3f
        val fit = with(density) { (constraints.maxWidth / units).toSp() }
        val size = if (fit.value < max.value) fit else max
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                amount,
                style = Zk.text.balance.copy(fontSize = size, lineHeight = size * 1.05f, letterSpacing = size * -0.045f),
                color = c.text,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "ZKA",
                style = Zk.text.mono.copy(fontSize = size * 0.4f, lineHeight = size * 0.5f),
                color = c.accent,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.alignByBaseline(),
            )
        }
    }
}

/** Punto de estado: late mientras algo está en curso. */
@Composable
fun StatusDot(pulsing: Boolean, modifier: Modifier = Modifier, color: Color = Zk.colors.accent) {
    Box(modifier.size(7.dp).alpha(if (pulsing) zkPulseAlpha() else 1f).background(color, CircleShape))
}

@Composable
fun VSpace(height: Int) = Spacer(Modifier.height(height.dp))
