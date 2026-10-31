package com.zoka.zkapriv.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zoka.zkapriv.ui.R

// ─────────────────────────────────────────────────────────────────────────────
//  Sistema de diseño de ZKAPriv 3, portado del de ZSilent
//  (zsilent-core/src/main/kotlin/com/zsilent/core/ui/theme/Theme.kt).
//
//  Reglas, las mismas que en escritorio:
//
//  1. Ningún Color(0x…) fuera de este archivo. Las pantallas leen Zk.colors.
//
//  2. Los grises son neutros puros. En oscuro, fondo y superficies son casi
//     iguales (#000000 / #020202 / #070707): lo que separa un panel es su
//     borde de 1 dp, no el relleno.
//
//  3. El acento es el rosa de Zoka Quantic (#F2ABD0 sobre negro, el de
//     zoka.network); en claro se oscurece a #A8326A para que se lea. Fuera del
//     acento, el color solo dice estado: éxito, aviso, peligro.
//
//  4. Inter para la interfaz y JetBrains Mono para todo dato: montos,
//     direcciones, alturas, hashes.
//
//  El Material 3 de este proyecto (BOM 2023.10) no tiene los roles
//  surfaceContainer*, así que las superficies viajan en ZkColors.
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class ZkColors(
    val dark: Boolean,
    val background: Color,
    /** Paneles. */
    val surface: Color,
    /** Campos, botones y filas por encima de un panel. */
    val surface2: Color,
    /** Diálogos y lo que se apoya encima de todo. */
    val elevated: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    /** El QR va siempre oscuro sobre claro, en los dos temas. */
    val qrDark: Color = Color(0xFF000000),
    val qrLight: Color = Color(0xFFFFFFFF),
)

/** Rosa de marca sobre fondo oscuro. */
private val QuanticPink = Color(0xFFF2ABD0)
/** Texto sobre el rosa: el casi negro cálido del botón de zoka.network. */
private val QuanticOnPink = Color(0xFF0A0709)
/** Rosa de marca en claro: 6,1:1 sobre el fondo. */
private val QuanticPinkDeep = Color(0xFFA8326A)

val ZkDark = ZkColors(
    dark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF020202),
    surface2 = Color(0xFF070707),
    elevated = Color(0xFF0F0F0F),
    border = Color(0xFF1B1B1B),
    borderStrong = Color(0xFF333333),
    text = Color(0xFFF8F8F8),
    textMuted = Color(0xFF868686),
    accent = QuanticPink,
    onAccent = QuanticOnPink,
    success = Color(0xFF6CD092),
    warning = Color(0xFFEFCA61),
    danger = Color(0xFFFF645F),
    info = Color(0xFFA7AEBB),
)

val ZkLight = ZkColors(
    dark = false,
    background = Color(0xFFFCFCFC),
    surface = Color(0xFFF5F5F5),
    surface2 = Color(0xFFEBEBEB),
    elevated = Color(0xFFFFFFFF),
    border = Color(0xFFD7D7D7),
    borderStrong = Color(0xFFB7B7B7),
    text = Color(0xFF090909),
    textMuted = Color(0xFF555555),
    accent = QuanticPinkDeep,
    onAccent = Color(0xFFFFFFFF),
    success = Color(0xFF0FA05C),
    warning = Color(0xFFCF9B00),
    danger = Color(0xFFD73337),
    info = Color(0xFF6B727E),
)

/** Lo que leen los componentes de Material que todavía se usan. */
private fun ZkColors.scheme(): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = border,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = surface2,
        onSecondary = text,
        secondaryContainer = surface2,
        onSecondaryContainer = text,
        tertiary = info,
        onTertiary = background,
        tertiaryContainer = border,
        onTertiaryContainer = text,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = surface2,
        onSurfaceVariant = textMuted,
        // Sin tinte: Material lo mezcla con las superficies elevadas.
        surfaceTint = surface,
        inverseSurface = text,
        inverseOnSurface = background,
        error = danger,
        onError = onAccent,
        errorContainer = danger.copy(alpha = 0.12f),
        onErrorContainer = danger,
        outline = borderStrong,
        outlineVariant = border,
        scrim = background.copy(alpha = 0.6f),
    )

// ── Tipografía ───────────────────────────────────────────────────────────────

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

val MonoFamily = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
)

/** Alternativas de Inter que usa el diseño. */
private const val INTER_FEATURES = "ss01, cv11"

/** Cifras de ancho fijo y cero barrado: obligatorio en datos. */
private const val MONO_FEATURES = "tnum, zero"

private fun inter(size: Int, weight: FontWeight, lineHeight: Int, tracking: Float = 0f) =
    TextStyle(
        fontFamily = InterFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        fontFeatureSettings = INTER_FEATURES,
    )

private fun mono(size: Int, weight: FontWeight, lineHeight: Int, tracking: Float = 0f) =
    TextStyle(
        fontFamily = MonoFamily,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = tracking.sp,
        fontFeatureSettings = MONO_FEATURES,
    )

// En el teléfono el cuerpo sube un punto respecto de escritorio: se lee a
// más distancia y con el dedo encima.
private val ZkTypography = Typography(
    displaySmall = inter(36, FontWeight.SemiBold, 40, -0.5f),
    headlineLarge = inter(30, FontWeight.SemiBold, 34, -0.4f),
    headlineMedium = inter(24, FontWeight.SemiBold, 28, -0.3f),
    headlineSmall = inter(20, FontWeight.SemiBold, 26, -0.2f),
    titleLarge = inter(17, FontWeight.SemiBold, 22, -0.2f),
    titleMedium = inter(15, FontWeight.SemiBold, 20),
    titleSmall = inter(14, FontWeight.Medium, 18),
    bodyLarge = inter(15, FontWeight.Normal, 22),
    bodyMedium = inter(14, FontWeight.Normal, 20),
    bodySmall = inter(12, FontWeight.Normal, 17),
    labelLarge = inter(13, FontWeight.Medium, 16, 0.5f),
    labelMedium = inter(12, FontWeight.Medium, 15, 0.5f),
    labelSmall = inter(11, FontWeight.Medium, 14, 0.5f),
)

/**
 * Lo que Material 3 no tiene. Ninguno pone mayúsculas por su cuenta: eso lo
 * hace cada componente con `.uppercase()`.
 */
@Immutable
data class ZkTextStyles(
    /** Eyebrow de sección, en rosa. 10 sp / 0,28 em. */
    val eyebrow: TextStyle,
    /** Título de panel. 11 sp / 0,18 em. */
    val panelLabel: TextStyle,
    /** Etiqueta de campo y de Stat. 10 sp / 0,18 em. */
    val fieldLabel: TextStyle,
    val pill: TextStyle,
    val mono: TextStyle,
    val monoLarge: TextStyle,
    /** El saldo principal. */
    val monoDisplay: TextStyle,
    /** ZOKA QUANTIC grande, como el h1 de zoka.network. */
    val brandHero: TextStyle,
    val brandTagline: TextStyle,
)

private val ZkText = ZkTextStyles(
    eyebrow = mono(10, FontWeight.Medium, 14, 2.8f),
    panelLabel = mono(11, FontWeight.Medium, 15, 1.98f),
    fieldLabel = mono(10, FontWeight.Medium, 14, 1.8f),
    pill = mono(10, FontWeight.Medium, 14, 0.5f),
    mono = mono(13, FontWeight.Normal, 19),
    monoLarge = mono(20, FontWeight.Normal, 24),
    monoDisplay = mono(34, FontWeight.Medium, 40),
    brandHero = inter(40, FontWeight.Medium, 42, -2f),
    brandTagline = inter(22, FontWeight.Normal, 27, -0.55f),
)

// ── Formas y medidas ─────────────────────────────────────────────────────────
// Tres radios y nada pasa de 10 dp salvo lo que es redondo (puntos, switches).

private val ZkShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(10.dp),
)

@Immutable
data class ZkSpacing(
    /** Relleno de un panel. */
    val panel: Dp = 16.dp,
    /** Entre paneles y entre bloques de un panel. */
    val gap: Dp = 12.dp,
    /** Relleno vertical de una fila. */
    val row: Dp = 10.dp,
    /** Alto de campos y botones: 48 dp, lo mínimo para el dedo. */
    val control: Dp = 48.dp,
    /** Margen lateral de la pantalla. */
    val screen: Dp = 16.dp,
)

// ── Movimiento ───────────────────────────────────────────────────────────────

/** `cubic-bezier(0.2, 0.8, 0.2, 1)`, el de ZSilent. */
val ZkEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
const val ZK_DURATION_FAST = 200
const val ZK_DURATION_ENTER = 350

val LocalZkColors = staticCompositionLocalOf { ZkDark }
val LocalZkText = staticCompositionLocalOf { ZkText }
val LocalZkSpacing = staticCompositionLocalOf { ZkSpacing() }

/**
 * Si es `false`, nada se anima: cada animación tiene que consultarlo. Sale de
 * la preferencia de la app y de "Quitar animaciones" del sistema.
 */
val LocalZkAnimations = staticCompositionLocalOf { true }

@Composable
fun ZkTheme(
    darkTheme: Boolean = true,
    animations: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) ZkDark else ZkLight
    val context = LocalContext.current
    val systemAnimations = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    CompositionLocalProvider(
        LocalZkColors provides colors,
        LocalZkText provides ZkText,
        LocalZkSpacing provides ZkSpacing(),
        LocalZkAnimations provides (animations && systemAnimations),
    ) {
        MaterialTheme(
            colorScheme = remember(colors) { colors.scheme() },
            typography = ZkTypography,
            shapes = ZkShapes,
            content = content,
        )
    }
}

/** Atajos, para no arrastrar `LocalX.current` por todos lados. */
object Zk {
    val colors: ZkColors
        @Composable get() = LocalZkColors.current
    val text: ZkTextStyles
        @Composable get() = LocalZkText.current
    val spacing: ZkSpacing
        @Composable get() = LocalZkSpacing.current
    val animations: Boolean
        @Composable get() = LocalZkAnimations.current
}
