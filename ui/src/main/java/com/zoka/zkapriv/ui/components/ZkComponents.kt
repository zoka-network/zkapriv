package com.zoka.zkapriv.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
//  Primitivos de ZKAPriv 4.1, con el estilo «negro y vidrio» de zoka.network.
//
//  Tres reglas: ningún color literal (todo sale de Zk.colors), toda animación
//  consulta Zk.animations antes de moverse y lo que se toca es cápsula. Los
//  botones principales y lo elegido son negros con letras rosa (zkInk).
// ─────────────────────────────────────────────────────────────────────────────

/** Tonos de estado. Solo On/Warn/Err llevan color de estado, y su punto late. */
enum class ZkTone { Default, On, Warn, Err, Off, Info, Accent }

@Composable
private fun ZkTone.foreground(): Color = when (this) {
    ZkTone.Default, ZkTone.Off -> Zk.colors.textMuted
    ZkTone.On -> Zk.colors.success
    ZkTone.Warn -> Zk.colors.warning
    ZkTone.Err -> Zk.colors.danger
    ZkTone.Info -> Zk.colors.text
    ZkTone.Accent -> Zk.colors.accentSoft
}

@Composable
private fun ZkTone.container(): Color = when (this) {
    ZkTone.Default, ZkTone.Off, ZkTone.Info -> Zk.colors.surface2
    ZkTone.Accent -> Zk.colors.accentWash
    else -> foreground().copy(alpha = 0.10f)
}

@Composable
private fun ZkTone.borderColor(): Color = when (this) {
    ZkTone.Default, ZkTone.Off -> Zk.colors.border
    ZkTone.Info -> Zk.colors.borderStrong
    ZkTone.Accent -> Zk.colors.accentLine
    else -> foreground().copy(alpha = 0.32f)
}

private val ZkTone.pulses: Boolean
    get() = this == ZkTone.On || this == ZkTone.Warn || this == ZkTone.Err

// ── Vidrio ───────────────────────────────────────────────────────────────────

/**
 * Tarjeta de vidrio: un degradé apenas más claro que el fondo y un borde de
 * 1 dp más luminoso arriba. `hero` marca la tarjeta principal de la pantalla.
 */
@Composable
fun Modifier.zkGlass(shape: Shape, hero: Boolean = false): Modifier {
    val c = Zk.colors
    return this
        .background(Brush.linearGradient(c.glass), shape)
        .border(BorderStroke(1.dp, Brush.verticalGradient(if (hero) c.heroEdge else c.glassEdge)), shape)
}

/** Cápsula neutra: botón secundario, pastilla, tecla, botón de ícono. */
@Composable
fun Modifier.zkCapsule(shape: Shape = CircleShape, pressed: Boolean = false): Modifier {
    val c = Zk.colors
    return this
        .background(Brush.verticalGradient(if (pressed) c.capsulePressed else c.capsule), shape)
        .border(BorderStroke(1.dp, c.borderStrong), shape)
}

/**
 * Lo elegido y los botones principales: negro en los dos temas, para letras
 * `onInk`. En oscuro el borde rosa se enciende al apretar.
 */
@Composable
fun Modifier.zkInk(shape: Shape = CircleShape, pressed: Boolean = false): Modifier {
    val c = Zk.colors
    return this
        .background(Brush.verticalGradient(if (pressed) c.inkPressed else c.ink), shape)
        .border(BorderStroke(1.dp, if (pressed && c.dark) c.accent else c.inkLine), shape)
}

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

/** Un temblor corto cada vez que `trigger` cambia: un PIN o una palabra errados. */
@Composable
fun Modifier.zkShake(trigger: Int): Modifier {
    val shift = remember { Animatable(0f) }
    val animate = Zk.animations
    LaunchedEffect(trigger) {
        if (trigger == 0 || !animate) return@LaunchedEffect
        for (x in listOf(-10f, 9f, -6f, 4f, -2f, 0f)) shift.animateTo(x, tween(45))
    }
    return this.offset { IntOffset(shift.value.dp.roundToPx(), 0) }
}

/** Lo que se aprieta se achica un 2 %. */
@Composable
private fun Modifier.pressScale(pressed: Boolean): Modifier {
    val scale by animateFloatAsState(
        if (pressed) 0.98f else 1f,
        if (Zk.animations) tween(120) else snap(),
        label = "zk-press",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// ── Contenedores ─────────────────────────────────────────────────────────────

/**
 * Tarjeta de vidrio. `hero` es la principal de una pantalla (el saldo, el QR,
 * la red): esquinas de 28 dp y borde más marcado. `gap` separa sus bloques
 * (0 para filas con divisores). Con `onClick`, toda la tarjeta se toca.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    right: @Composable (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Zk.spacing.panel),
    gap: Dp = Zk.spacing.gap,
    hero: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = if (hero) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .zkGlass(shape, hero)
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(gap),
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

/** El puntito rosa de la web. */
@Composable
fun AccentDot(modifier: Modifier = Modifier, size: Dp = 6.dp, color: Color = Zk.colors.accent) {
    Box(modifier.size(size).background(color, CircleShape))
}

/**
 * Eyebrow en mono mayúscula y gris, como los de zoka.network; `dot` le
 * antepone el puntito rosa, que es el detalle de color.
 */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, dot: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (dot) AccentDot()
        Text(text.uppercase(), style = Zk.text.eyebrow, color = Zk.colors.textMuted, maxLines = 1, softWrap = false)
    }
}

/** Encabezado de sección: eyebrow (si hay), título y bajada. */
@Composable
fun SectionTitle(eyebrow: String?, title: String, sub: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (eyebrow != null) Eyebrow(eyebrow)
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Zk.colors.text)
        if (sub != null) {
            Text(sub, style = Zk.text.lead, color = Zk.colors.textSoft)
        }
    }
}

/** Ícono en un recuadro: filas de ajustes, movimientos, billeteras. */
@Composable
fun IconSquare(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Zk.colors.textSoft,
    active: Boolean = false,
    size: Dp = 40.dp,
) {
    val c = Zk.colors
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .size(size)
            .background(c.surface2, shape)
            .border(BorderStroke(1.dp, if (active) c.accentLine else c.border), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (active) c.accent else tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** La tilde de «listo» o «en uso»: negra con el trazo rosa, como los botones. */
@Composable
fun CheckBadge(modifier: Modifier = Modifier) {
    Box(modifier.size(22.dp).zkInk(), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Check, contentDescription = null, tint = Zk.colors.onInk, modifier = Modifier.size(14.dp))
    }
}

/**
 * El arco de luz de zoka.network, muy tenue: un círculo enorme del que se ve
 * solo la parte de abajo, con un resplandor. Es el único adorno de la app y va
 * solo en la bienvenida y el bloqueo; nunca en el inicio.
 */
@Composable
fun GlassArc(modifier: Modifier = Modifier, towardsEnd: Boolean = false) {
    val c = Zk.colors
    val glow = c.text.copy(alpha = if (c.dark) 0.07f else 0.035f)
    Canvas(modifier.fillMaxSize()) {
        val radius = size.width * 1.05f
        val center = Offset(
            if (towardsEnd) size.width * 0.95f else size.width / 2f,
            size.height * 0.30f - radius,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(center.x, center.y + radius * 0.86f),
                radius = radius * 0.55f,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(c.border, radius, center, style = Stroke(1.dp.toPx()))
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
    val fg = tone.foreground()
    val alpha = if (tone.pulses) zkPulseAlpha() else 1f
    Row(
        modifier = modifier
            .background(tone.container(), CircleShape)
            .border(BorderStroke(1.dp, tone.borderColor()), CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone != ZkTone.Default && tone != ZkTone.Off && tone != ZkTone.Accent) {
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Zk.colors.textMuted)
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
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Zk.colors.textMuted)
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

/** Medidor fino (sincronización, partes del QR). */
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
    /** La acción principal de una pantalla: negro con letras rosa. Una por pantalla. */
    Primary,
    /** Las demás: cápsula de vidrio con letras del color del texto. */
    Secondary,
    /** Solo texto: cancelar, saltar, un enlace. */
    Ghost,
    Danger,
}

enum class ZButtonSize { Small, Medium, Large }

/** Botón: cápsula, texto mono en mayúscula y sin elevación. */
@Composable
fun ZButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ZButtonVariant = ZButtonVariant.Secondary,
    size: ZButtonSize = ZButtonSize.Medium,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val c = Zk.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val active = pressed && enabled
    val fg = when (variant) {
        ZButtonVariant.Primary -> c.onInk
        ZButtonVariant.Secondary -> c.text
        ZButtonVariant.Ghost -> if (active) c.text else c.textMuted
        ZButtonVariant.Danger -> c.danger
    }
    val height = when (size) {
        ZButtonSize.Small -> 36.dp
        ZButtonSize.Medium -> Zk.spacing.control
        ZButtonSize.Large -> 54.dp
    }
    val horizontal = when (size) {
        ZButtonSize.Small -> 14.dp
        ZButtonSize.Medium -> 18.dp
        ZButtonSize.Large -> 22.dp
    }
    val look = when (variant) {
        ZButtonVariant.Primary -> Modifier.zkInk(pressed = active)
        ZButtonVariant.Secondary -> Modifier.zkCapsule(pressed = active)
        ZButtonVariant.Ghost -> Modifier.background(if (active) c.surface2 else Color.Transparent, CircleShape)
        ZButtonVariant.Danger -> Modifier
            .background(c.danger.copy(alpha = if (active) 0.18f else 0.08f), CircleShape)
            .border(BorderStroke(1.dp, c.danger.copy(alpha = 0.42f)), CircleShape)
    }
    Row(
        modifier = modifier
            .heightIn(min = height)
            .alpha(if (enabled) 1f else 0.38f)
            .pressScale(active)
            .then(look)
            .clip(CircleShape)
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
        style = Zk.text.button,
        color = LocalContentColor.current,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
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

/** La marca chica de la web: `ZOKA QUANTIC` en mono, con mucho aire. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Text("ZOKA QUANTIC", modifier = modifier, style = Zk.text.brandMark, color = Zk.colors.textSoft, maxLines = 1, softWrap = false)
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
    textStyle: TextStyle? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val c = Zk.colors
    var focused by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    val border = animatedColor(
        when {
            isError -> c.danger.copy(alpha = 0.7f)
            focused -> c.accentLine
            else -> c.border
        },
        "zk-input-border",
    )
    val style = (textStyle ?: if (mono) Zk.text.mono else MaterialTheme.typography.bodyLarge).copy(color = c.text)
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
                Modifier.fillMaxWidth().padding(start = 14.dp, end = if (trailing != null) 6.dp else 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            style = style,
                            color = c.textFaint,
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

/**
 * Interruptor: prendido es negro con la pastilla rosa, como los botones. Sin
 * `onCheckedChange` solo se dibuja: lo toca la fila que lo contiene.
 */
@Composable
fun ZToggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
) {
    val c = Zk.colors
    val track = if (checked) Modifier.zkInk() else Modifier
        .background(c.surface2, CircleShape)
        .border(BorderStroke(1.dp, c.borderStrong), CircleShape)
    Box(
        modifier
            .size(width = 48.dp, height = 28.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .then(track)
            .let {
                if (onCheckedChange == null) it else it
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
                    .semantics { if (description != null) contentDescription = description }
            },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .size(20.dp)
                .background(if (checked) c.onInk else c.textMuted, CircleShape),
        )
    }
}

/** Selector de una opción entre pocas: la elegida es negra con letras rosa. */
@Composable
fun Segmented(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Zk.colors
    Row(
        modifier
            .background(c.surface2, CircleShape)
            .border(BorderStroke(1.dp, c.border), CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 36.dp)
                    .then(if (active) Modifier.zkInk() else Modifier)
                    .clip(CircleShape)
                    .clickable(enabled = enabled, role = Role.Tab) { onSelect(option) }
                    .semantics { this.selected = active },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option.uppercase(),
                    style = Zk.text.button.copy(fontSize = 11.sp, letterSpacing = 1.3.sp),
                    color = when {
                        !enabled -> c.textMuted.copy(alpha = 0.5f)
                        active -> c.onInk
                        else -> c.textMuted
                    },
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Copiar con confirmación de 1,2 s. */
@Composable
fun CopyButton(
    value: String,
    modifier: Modifier = Modifier,
    label: String = "Copy",
    variant: ZButtonVariant = ZButtonVariant.Secondary,
    size: ZButtonSize = ZButtonSize.Small,
) {
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
        variant = variant,
        size = size,
        enabled = value.isNotBlank(),
    ) {
        Icon(
            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = LocalContentColor.current,
            modifier = Modifier.size(15.dp),
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
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(enabled = value.isNotBlank(), role = Role.Button) {
                clipboard.setText(AnnotatedString(value))
                copied = true
            }
            .semantics { contentDescription = "Copy $what" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
            contentDescription = null,
            tint = if (copied) Zk.colors.accent else Zk.colors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Pastilla de un selector: una elegida entre varias, o una acción corta.
 * `leading` va antes del texto (un ícono chico).
 */
@Composable
fun ZChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    /** `Role.Tab` si elige entre varias; `Role.Button` si es una acción. */
    role: Role = Role.Tab,
    leading: @Composable (() -> Unit)? = null,
) {
    val c = Zk.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fg = if (selected) c.onInk else c.textSoft
    Row(
        modifier
            .heightIn(min = 38.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (selected) Modifier.zkInk(pressed = pressed) else Modifier.zkCapsule(pressed = pressed))
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = role,
                onClick = onClick,
            )
            .semantics { if (role == Role.Tab) this.selected = selected }
            .padding(horizontal = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            leading?.invoke()
            Text(text, style = Zk.text.mono.copy(fontSize = 12.sp), color = fg, maxLines = 1, softWrap = false)
        }
    }
}

/** Botón redondo de 44 dp con un ícono: volver, escanear, la linterna. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Zk.colors.text,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .then(if (selected) Modifier.zkInk(pressed = pressed) else Modifier.zkCapsule(pressed = pressed))
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) Zk.colors.onInk else tint,
            modifier = Modifier.size(21.dp),
        )
    }
}

/** Barritas de paso de un alta: las hechas y la actual, en rosa. */
@Composable
fun StepBars(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Step $current of $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(width = 22.dp, height = 4.dp)
                    .background(if (i < current) Zk.colors.accent else Zk.colors.borderStrong, CircleShape),
            )
        }
    }
}

/**
 * Hoja que sube desde abajo, para confirmar o elegir sin salir de la pantalla.
 * Es un `Dialog`: hereda el bloqueo de capturas de la ventana.
 */
@Composable
fun ZSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Zk.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val animate = Zk.animations
    val shown = remember { MutableTransitionState(!animate).apply { targetState = true } }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visibleState = shown,
                enter = slideInVertically(tween(ZK_DURATION_ENTER, easing = ZkEasing)) { it / 3 } +
                    fadeIn(tween(ZK_DURATION_FAST)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.elevated, shape)
                        .border(BorderStroke(1.dp, Brush.verticalGradient(c.heroEdge)), shape)
                        // Tocar la hoja no la cierra.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        )
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(Zk.spacing.gap),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .background(c.borderStrong, CircleShape),
                    )
                    CompositionLocalProvider(LocalContentColor provides c.text) { content() }
                }
            }
        }
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
 * QR estándar sobre una placa blanca: módulos cuadrados, esquinas como manda
 * la norma, 4 módulos de margen y SIEMPRE negro sobre blanco, en los dos
 * temas. Muchos lectores no leen un QR invertido, y esto es una dirección de
 * cobro.
 */
@Composable
fun ZkQrCode(
    value: String,
    modifier: Modifier = Modifier,
    errorCorrection: ErrorCorrectionLevel = ErrorCorrectionLevel.M,
    boxSize: Dp = 232.dp,
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
    val shape = MaterialTheme.shapes.medium
    Box(
        modifier = modifier
            .size(boxSize)
            .background(c.qrLight, shape)
            .border(BorderStroke(1.dp, c.border), shape)
            .padding(6.dp),
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
            .zkGlass(MaterialTheme.shapes.large)
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(48.dp).zkCapsule(), contentAlignment = Alignment.Center) {
            Icon(
                icon ?: Icons.Outlined.Inbox,
                contentDescription = null,
                tint = c.textMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = c.text, textAlign = TextAlign.Center)
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
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier
            .fillMaxWidth()
            .background(tone.container(), shape)
            .border(BorderStroke(1.dp, tone.borderColor()), shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.padding(top = 6.dp).size(6.dp).background(tone.foreground(), CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Zk.colors.text)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = Zk.colors.textSoft)
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
