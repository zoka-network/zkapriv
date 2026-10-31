package com.zoka.zkapriv.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.zoka.zkapriv.ui.theme.ZK_DURATION_ENTER
import com.zoka.zkapriv.ui.theme.ZK_DURATION_FAST
import com.zoka.zkapriv.ui.theme.Zk
import com.zoka.zkapriv.ui.theme.ZkEasing
import kotlinx.coroutines.delay
import kotlin.math.floor

// ─────────────────────────────────────────────────────────────────────────────
//  Primitivos de ZKAPriv 3, portados de ZSilent (ui/components/Components.kt).
//
//  Dos reglas: ningún color literal (todo sale de Zk.colors) y toda animación
//  consulta Zk.animations antes de moverse. Donde en escritorio algo cambia
//  al pasar el mouse, acá cambia al apretar.
// ─────────────────────────────────────────────────────────────────────────────

/** Tonos de estado. Solo On/Warn/Err llevan color, y su punto late. */
enum class ZkTone { Default, On, Warn, Err, Off, Info, Accent }

@Composable
private fun ZkTone.foreground(): Color = when (this) {
    ZkTone.Default -> Zk.colors.textMuted
    ZkTone.On -> Zk.colors.success
    ZkTone.Warn -> Zk.colors.warning
    ZkTone.Err -> Zk.colors.danger
    ZkTone.Off -> Zk.colors.textMuted.copy(alpha = 0.7f)
    ZkTone.Info -> Zk.colors.text
    ZkTone.Accent -> Zk.colors.accent
}

@Composable
private fun ZkTone.container(): Color = when (this) {
    ZkTone.Default, ZkTone.Off, ZkTone.Info -> Zk.colors.surface2
    else -> foreground().copy(alpha = 0.10f)
}

@Composable
private fun ZkTone.borderColor(): Color = when (this) {
    ZkTone.Default, ZkTone.Off -> Zk.colors.border
    ZkTone.Info -> Zk.colors.borderStrong
    else -> foreground().copy(alpha = 0.30f)
}

private val ZkTone.pulses: Boolean
    get() = this == ZkTone.On || this == ZkTone.Warn || this == ZkTone.Err

// ── Movimiento ───────────────────────────────────────────────────────────────

/** Alfa que late entre 1 y 0,35 en 1,6 s; quieto si no hay animaciones. */
@Composable
fun zkPulseAlpha(): Float {
    if (!Zk.animations) return 1f
    val transition = rememberInfiniteTransition(label = "zk-pulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "zk-pulse-alpha",
    ).value
}

/** Contenido que entra subiendo 8 dp y apareciendo al cambiar de `target`. */
@Composable
fun <T> ZkAnimatedSwap(
    target: T,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    if (!Zk.animations) {
        Box(modifier) { content(target) }
        return
    }
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(ZK_DURATION_ENTER, easing = ZkEasing)) +
                slideInVertically(tween(ZK_DURATION_ENTER, easing = ZkEasing)) { it / 12 })
                .togetherWith(fadeOut(tween(ZK_DURATION_FAST)))
        },
        label = "zk-in",
    ) { content(it) }
}

@Composable
private fun animatedColor(target: Color, label: String): Color {
    val color by animateColorAsState(
        target,
        if (Zk.animations) tween(ZK_DURATION_FAST) else snap(),
        label = label,
    )
    return color
}

// ── Contenedores ─────────────────────────────────────────────────────────────

/** Panel: relleno casi igual al fondo y borde de 1 dp, que es lo que separa. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    right: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Zk.spacing.panel),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Zk.colors.surface, shape)
            .border(BorderStroke(1.dp, Zk.colors.border), shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(Zk.spacing.gap),
    ) {
        CompositionLocalProvider(LocalContentColor provides Zk.colors.text) {
            if (title != null) PanelHeader(title = title, right = right)
            content()
        }
    }
}

/** Cabecera de panel: etiqueta mono en mayúscula y un lugar a la derecha. */
@Composable
fun PanelHeader(
    title: String,
    subtitle: String? = null,
    right: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.uppercase(), style = Zk.text.panelLabel, color = Zk.colors.textMuted)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
            }
        }
        right?.invoke()
    }
}

/** Eyebrow rosa en mono mayúscula: la firma visual del diseño. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = Zk.text.eyebrow, color = Zk.colors.accent)
}

/** Encabezado de sección: eyebrow, título y bajada. */
@Composable
fun SectionTitle(eyebrow: String, title: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Zk.colors.text)
        if (sub != null) {
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = Zk.colors.textMuted)
        }
    }
}

// ── Datos ────────────────────────────────────────────────────────────────────

/** Etiqueta chica en mono mayúscula, valor grande en mono y una pista. */
@Composable
fun Stat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    tone: ZkTone = ZkTone.Default,
) {
    val valueColor = when (tone) {
        ZkTone.Default -> Zk.colors.text
        else -> tone.foreground()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.uppercase(), style = Zk.text.fieldLabel, color = Zk.colors.textMuted)
        Text(
            value,
            style = Zk.text.monoLarge,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hint != null) {
            Text(hint, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
        }
    }
}

/** Insignia de estado con punto; los tonos activos laten. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, tone: ZkTone = ZkTone.Default) {
    val shape = MaterialTheme.shapes.extraSmall
    val fg = tone.foreground()
    val alpha = if (tone.pulses) zkPulseAlpha() else 1f
    Row(
        modifier = modifier
            .background(tone.container(), shape)
            .border(BorderStroke(1.dp, tone.borderColor()), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone != ZkTone.Default && tone != ZkTone.Off) {
            Box(Modifier.size(6.dp).alpha(alpha).background(fg, CircleShape))
        }
        Text(text.uppercase(), style = Zk.text.pill, color = fg, maxLines = 1)
    }
}

/**
 * Un valor largo cortado por el medio: quedan el principio y el final, que es
 * lo que se compara. Lo que se copia es siempre el valor entero.
 */
fun middleTruncated(value: String, head: Int = 18, tail: Int = 12): String =
    if (value.length <= head + tail + 1) value else value.take(head) + "…" + value.takeLast(tail)

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Zk.colors.text,
    maxLines: Int = 1,
) {
    Text(
        text,
        modifier = modifier,
        style = Zk.text.mono,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(vertical = Zk.spacing.row / 2),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
        Text(
            value,
            style = Zk.text.mono,
            color = Zk.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
    }
}

/**
 * Un valor largo con su etiqueta arriba, cortado al medio (16/12: lo que
 * entra en el ancho de un teléfono) y un botón que copia el entero.
 */
@Composable
fun CopyableKeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    val shown = remember(value) { middleTruncated(value, head = 16, tail = 12) }
    Column(modifier.fillMaxWidth().padding(top = Zk.spacing.row / 2)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                shown,
                modifier = Modifier.weight(1f),
                style = Zk.text.mono,
                color = Zk.colors.text,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            CopyIconButton(value, label)
        }
    }
}

/** Medidor fino (sincronización, progreso de la prueba). */
@Composable
fun MeterBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Zk.colors.accent) {
    val safe = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(Zk.colors.surface2, CircleShape),
    ) {
        Box(Modifier.fillMaxWidth(safe).height(4.dp).background(color, CircleShape))
    }
}

// ── Controles ────────────────────────────────────────────────────────────────

enum class ZButtonVariant {
    /** La acción principal de una pantalla: relleno rosa. Una por pantalla. */
    Primary,
    /** Contorno rosa que se rellena al apretar, como el botón de zoka.network. */
    Accent,
    Default,
    Ghost,
    Danger,
    Outline,
}

enum class ZButtonSize { Small, Medium, Large }

/** Botón: texto en mayúscula, borde de 1 dp y sin elevación. */
@Composable
fun ZButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ZButtonVariant = ZButtonVariant.Default,
    size: ZButtonSize = ZButtonSize.Medium,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Zk.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = pressed && enabled
    val container = when (variant) {
        ZButtonVariant.Primary -> if (active) c.accent.copy(alpha = 0.85f) else c.accent
        ZButtonVariant.Accent -> if (active) c.accent else Color.Transparent
        ZButtonVariant.Default -> if (active) c.elevated else c.surface2
        ZButtonVariant.Ghost -> if (active) c.surface2 else Color.Transparent
        ZButtonVariant.Danger -> c.danger.copy(alpha = if (active) 0.20f else 0.10f)
        ZButtonVariant.Outline -> if (active) c.surface2 else Color.Transparent
    }
    val fg = when (variant) {
        ZButtonVariant.Primary -> c.onAccent
        ZButtonVariant.Accent -> if (active) c.onAccent else c.text
        ZButtonVariant.Default, ZButtonVariant.Outline -> c.text
        ZButtonVariant.Ghost -> if (active) c.text else c.textMuted
        ZButtonVariant.Danger -> c.danger
    }
    val borderTarget = when (variant) {
        ZButtonVariant.Primary, ZButtonVariant.Accent -> c.accent
        ZButtonVariant.Default -> if (active) c.borderStrong else c.border
        ZButtonVariant.Ghost -> Color.Transparent
        ZButtonVariant.Danger -> c.danger.copy(alpha = 0.40f)
        ZButtonVariant.Outline -> c.borderStrong
    }
    val border = animatedColor(borderTarget, "zk-button-border")
    val height = when (size) {
        ZButtonSize.Small -> 36.dp
        ZButtonSize.Medium -> Zk.spacing.control
        ZButtonSize.Large -> Zk.spacing.control + 4.dp
    }
    val horizontal = when (size) {
        ZButtonSize.Small -> 12.dp
        ZButtonSize.Medium -> 16.dp
        ZButtonSize.Large -> 20.dp
    }
    val shape = MaterialTheme.shapes.small
    Row(
        modifier = modifier
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.4f)
            .background(container, shape)
            .border(BorderStroke(1.dp, border), shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = horizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

/** Texto de botón, con la mayúscula y el tracking puestos. */
@Composable
fun ZButtonText(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = LocalContentColor.current,
        maxLines = 1,
    )
}

/** "ZOKA" en el color del texto y "QUANTIC" en rosa, en una sola línea. */
@Composable
fun QuanticWordmark(
    style: TextStyle,
    modifier: Modifier = Modifier,
    suffix: String? = null,
) {
    val c = Zk.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.text)) { append("ZOKA ") }
            withStyle(SpanStyle(color = c.accent)) { append("QUANTIC") }
            if (suffix != null) {
                withStyle(SpanStyle(color = c.textMuted)) { append(" · $suffix") }
            }
        },
        style = style,
        maxLines = 1,
        softWrap = false,
        modifier = modifier,
    )
}

/** Etiqueta de campo: mono, mayúscula, tracking ancho. */
@Composable
fun ZLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = Zk.text.fieldLabel, color = Zk.colors.textMuted)
}

/**
 * Campo de texto: relleno `surface2`, texto mono y borde rosa con el foco.
 * Sobre `BasicTextField` porque el de Material impone su propio alto.
 */
@Composable
fun ZInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    mono: Boolean = true,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Zk.colors
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.small
    val border = animatedColor(
        when {
            isError -> c.danger.copy(alpha = 0.6f)
            focused -> c.accent.copy(alpha = 0.6f)
            else -> c.border
        },
        "zk-input-border",
    )
    val style = (if (mono) Zk.text.mono else MaterialTheme.typography.bodyLarge).copy(color = c.text)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = Zk.spacing.control)
            .background(if (focused) c.elevated else c.surface2, shape)
            .border(BorderStroke(1.dp, border), shape)
            .onFocusChanged { focused = it.isFocused },
        enabled = enabled,
        singleLine = singleLine,
        textStyle = style,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        cursorBrush = SolidColor(c.accent),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = style,
                            color = c.textMuted.copy(alpha = 0.7f),
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (trailing != null) {
                    Spacer(Modifier.width(4.dp))
                    trailing()
                }
            }
        },
    )
}

/** Interruptor: pista de 44×24 dp con pastilla de 16 dp; rosa prendido. */
@Composable
fun ZToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    val c = Zk.colors
    Box(
        modifier
            .size(width = 44.dp, height = 24.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .background(if (checked) c.accent.copy(alpha = 0.3f) else c.surface2, CircleShape)
            .border(BorderStroke(1.dp, if (checked) c.accent else c.border), CircleShape)
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(16.dp)
                .background(if (checked) c.accent else c.textMuted, CircleShape),
        )
    }
}

/** Selector de una opción entre pocas. */
@Composable
fun Segmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Zk.colors
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .background(c.surface2, shape)
            .border(BorderStroke(1.dp, c.border), shape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .background(
                        if (active) c.accent.copy(alpha = 0.20f) else Color.Transparent,
                        MaterialTheme.shapes.extraSmall,
                    )
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.uppercase(),
                    style = Zk.text.pill,
                    color = when {
                        !enabled -> c.textMuted.copy(alpha = 0.5f)
                        active -> c.accent
                        else -> c.textMuted
                    },
                )
            }
        }
    }
}

/** Copiar con confirmación de 1,2 s. */
@Composable
fun CopyButton(value: String, modifier: Modifier = Modifier, label: String = "Copy") {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }
    ZButton(
        onClick = {
            clipboard.setText(AnnotatedString(value))
            copied = true
        },
        modifier = modifier,
        size = ZButtonSize.Small,
        enabled = value.isNotBlank(),
    ) {
        Icon(
            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = if (copied) Zk.colors.success else LocalContentColor.current,
            modifier = Modifier.size(14.dp),
        )
        ZButtonText(if (copied) "Copied" else label)
    }
}

@Composable
fun CopyIconButton(value: String, what: String = "value") {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }
    IconButton(
        onClick = {
            clipboard.setText(AnnotatedString(value))
            copied = true
        },
        enabled = value.isNotBlank(),
    ) {
        Icon(
            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = "Copy $what",
            tint = if (copied) Zk.colors.success else Zk.colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── QR ───────────────────────────────────────────────────────────────────────

/** Margen blanco alrededor del QR, en módulos: el que pide la norma. */
const val QR_QUIET_ZONE_MODULES = 4

/** Corrida de módulos oscuros seguidos en la fila `y`, de `from` a `to` (sin incluirlo). */
data class QrRun(val y: Int, val from: Int, val to: Int)

fun qrDarkRuns(qr: BitMatrix): List<QrRun> {
    val runs = ArrayList<QrRun>()
    for (y in 0 until qr.height) {
        var x = 0
        while (x < qr.width) {
            if (!qr[x, y]) {
                x++
                continue
            }
            val start = x
            while (x < qr.width && qr[x, y]) x++
            runs.add(QrRun(y, start, x))
        }
    }
    return runs
}

/**
 * QR estándar: módulos cuadrados, esquinas como manda la norma, 4 módulos de
 * margen y SIEMPRE negro sobre blanco, en los dos temas. Muchos lectores no
 * leen un QR invertido, y esto es una dirección de cobro.
 */
@Composable
fun ZkQrCode(
    value: String,
    modifier: Modifier = Modifier,
    errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
    boxSize: Dp = 240.dp,
) {
    val matrix = remember(value, errorCorrection) {
        runCatching {
            QRCodeWriter().encode(
                value,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to errorCorrection,
                    EncodeHintType.MARGIN to QR_QUIET_ZONE_MODULES,
                ),
            )
        }.getOrNull()
    }
    val runs = remember(matrix) { matrix?.let(::qrDarkRuns).orEmpty() }
    val c = Zk.colors
    Box(
        modifier = modifier
            .size(boxSize)
            .background(c.surface2, MaterialTheme.shapes.medium)
            .border(BorderStroke(1.dp, c.border), MaterialTheme.shapes.medium)
            .padding(10.dp)
            .background(c.qrLight, MaterialTheme.shapes.small),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val qr = matrix ?: return@Canvas
            // Píxeles enteros por módulo: con medidas fraccionarias queda una
            // línea clara entre cuadrados vecinos que confunde al lector.
            val cell = floor(minOf(size.width / qr.width, size.height / qr.height)).coerceAtLeast(1f)
            val originX = floor((size.width - cell * qr.width) / 2f)
            val originY = floor((size.height - cell * qr.height) / 2f)
            for (run in runs) {
                drawRect(
                    color = c.qrDark,
                    topLeft = Offset(originX + run.from * cell, originY + run.y * cell),
                    size = Size((run.to - run.from) * cell, cell),
                )
            }
        }
    }
}

// ── Estados ──────────────────────────────────────────────────────────────────

@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: ImageVector? = null,
) {
    val c = Zk.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, c.border), MaterialTheme.shapes.medium)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            icon ?: Icons.Outlined.Inbox,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(22.dp),
        )
        Text(text, style = MaterialTheme.typography.titleSmall, color = c.text, textAlign = TextAlign.Center)
        if (sub != null) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}

/** Aviso en línea: borde y punto del tono, texto en los colores de siempre. */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    tone: ZkTone = ZkTone.Warn,
    action: @Composable (() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.small
    Row(
        modifier
            .fillMaxWidth()
            .background(tone.container(), shape)
            .border(BorderStroke(1.dp, tone.borderColor()), shape)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).background(tone.foreground(), CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Zk.colors.text)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textMuted)
            action?.invoke()
        }
    }
}

@Composable
fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Zk.colors.danger, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun HintText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = Zk.colors.textMuted, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Zk.colors.border))
}
