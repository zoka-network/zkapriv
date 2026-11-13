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
//  Sistema de diseño de ZKAPriv 4.1: «negro y vidrio», el del home de
//  zoka.network (zokaweb/src/index.css y components/zoka/home.css).
//
//  Reglas:
//
//  1. Ningún Color(0x…) fuera de este archivo. Las pantallas leen Zk.colors.
//
//  2. Negro profundo y piezas de vidrio. El vidrio no es desenfoque: es un
//     degradé apenas más claro que el fondo, un borde de 1 dp más luminoso
//     arriba y nada de sombra. Es barato en un teléfono de 32 bits.
//
//  3. Los botones principales y lo elegido (pestaña, pastilla, interruptor)
//     son negros con letras rosa suave, EN LOS DOS TEMAS: `ink` y `onInk`. En
//     oscuro el negro es de vidrio con borde rosa fino; en claro es sólido
//     sobre la página blanca. No hay botones rellenos de rosa.
//
//  4. El rosa es solo detalle. Sobre la página se usa `accent` (#F2ABD0 en
//     oscuro; en claro #A8326A, que se lee a 6:1 sobre blanco); sobre negro,
//     `onInk`. Fuera del rosa, el color solo dice estado: éxito, aviso,
//     peligro.
//
//  5. Inter para la interfaz (Light para las bajadas, como en la web) y
//     JetBrains Mono para todo dato y para etiquetas, eyebrows y botones en
//     mayúscula.
//
//  6. Cápsula para botones, pastillas, segmentado y barra inferior; 28 dp las
//     tarjetas principales, 20 dp las demás, 16 dp campos y avisos, 12 dp
//     recuadros y casillas.
//
//  El Material 3 de este proyecto (BOM 2023.10) no tiene los roles
//  surfaceContainer*, así que las superficies viajan en ZkColors.
// ─────────────────────────────────────────────────────────────────────────────

@Immutable
data class ZkColors(
    val dark: Boolean,
    val background: Color,
    /** Base de las tarjetas. */
    val surface: Color,
    /** Campos, casillas y recuadros por encima de una tarjeta. */
    val surface2: Color,
    /** Hojas y diálogos. */
    val elevated: Color,
    val border: Color,
    val borderStrong: Color,
    val text: Color,
    /** Cuerpo de texto: un escalón más suave que `text`. */
    val textSoft: Color,
    /** Etiquetas y texto secundario. */
    val textMuted: Color,
    /** Placeholders. Sigue leyéndose a más de 4,5:1. */
    val textFaint: Color,
    /** Rosa sobre la página: puntito, ícono activo, ZKA, PIN. */
    val accent: Color,
    /** Letras rosa sobre la página: enlaces y eyebrows. */
    val accentSoft: Color,
    /** Borde rosa fino: foco, pastilla de estado. */
    val accentLine: Color,
    /** Fondo rosa muy tenue. */
    val accentWash: Color,
    val onAccent: Color,
    /** Relleno (de arriba abajo) de los botones principales y de lo elegido. */
    val ink: List<Color>,
    val inkPressed: List<Color>,
    val inkLine: Color,
    /** Letras sobre `ink`: rosa suave en los dos temas. */
    val onInk: Color,
    /** Degradé de las tarjetas de vidrio, de arriba a la izquierda hacia abajo. */
    val glass: List<Color>,
    /** Borde de una tarjeta, de arriba abajo: más luminoso arriba. */
    val glassEdge: List<Color>,
    /** Lo mismo para la tarjeta principal de una pantalla. */
    val heroEdge: List<Color>,
    /** Relleno de las cápsulas neutras: botón secundario, pastillas, teclas. */
    val capsule: List<Color>,
    val capsulePressed: List<Color>,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
    /** El QR va siempre oscuro sobre claro, en los dos temas. */
    val qrDark: Color = Color(0xFF000000),
    val qrLight: Color = Color(0xFFFFFFFF),
)

/** Rosa de marca sobre fondo oscuro: el de zoka.network. */
private val QuanticPink = Color(0xFFF2ABD0)
/** El de los enlaces de la web: un punto más apagado, para letras. */
private val QuanticPinkSoft = Color(0xFFE8BDD5)
/** Rosa de marca sobre blanco: 6,3:1. */
private val QuanticPinkDeep = Color(0xFFA8326A)
/** El negro de los botones del tema claro. */
private val Ink = Color(0xFF0A0A0B)

val ZkDark = ZkColors(
    dark = true,
    background = Color(0xFF000000),
    surface = Color(0xFF050506),
    surface2 = Color(0xFF0B0B0D),
    elevated = Color(0xFF111114),
    border = Color(0x1FFFFFFF),
    borderStrong = Color(0x40FFFFFF),
    text = Color(0xFFFAFAFA),
    textSoft = Color(0xFFB4B4BA),
    textMuted = Color(0xFF8B8B92),
    textFaint = Color(0xFF7C7C84),
    accent = QuanticPink,
    accentSoft = QuanticPinkSoft,
    accentLine = Color(0x73F2ABD0),
    accentWash = Color(0x14F2ABD0),
    onAccent = Color(0xFF0A0709),
    // Blanco al 8 % y al 1,5 % sobre negro, ya compuesto.
    ink = listOf(Color(0xFF141414), Color(0xFF040404)),
    // Con un velo rosa: se nota que se apretó.
    inkPressed = listOf(Color(0xFF22181D), Color(0xFF0F0A0C)),
    inkLine = Color(0x80F2ABD0),
    onInk = QuanticPinkSoft,
    // Blanco al 7 %, 1,5 % y 3,5 % sobre #050506, ya compuesto.
    glass = listOf(Color(0xFF161617), Color(0xFF09090A), Color(0xFF0E0E0F)),
    glassEdge = listOf(Color(0x42FFFFFF), Color(0x1AFFFFFF)),
    heroEdge = listOf(Color(0x57FFFFFF), Color(0x24FFFFFF)),
    capsule = listOf(Color(0xFF141414), Color(0xFF040404)),
    capsulePressed = listOf(Color(0xFF222222), Color(0xFF0E0E0E)),
    success = Color(0xFF6CD092),
    warning = Color(0xFFEFCA61),
    danger = Color(0xFFFF645F),
    info = Color(0xFFA1A1A8),
)

val ZkLight = ZkColors(
    dark = false,
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFAFAFA),
    surface2 = Color(0xFFF4F4F5),
    elevated = Color(0xFFFFFFFF),
    border = Color(0x1A000000),
    borderStrong = Color(0x33000000),
    text = Ink,
    textSoft = Color(0xFF3F3F46),
    textMuted = Color(0xFF63636B),
    textFaint = Color(0xFF6E6E76),
    accent = QuanticPinkDeep,
    accentSoft = QuanticPinkDeep,
    accentLine = Color(0x66A8326A),
    accentWash = Color(0x12A8326A),
    onAccent = Color(0xFFFFFFFF),
    ink = listOf(Ink, Ink),
    inkPressed = listOf(Color(0xFF2A2A2E), Color(0xFF2A2A2E)),
    inkLine = Ink,
    onInk = QuanticPink,
    glass = listOf(Color(0xFFFFFFFF), Color(0xFFFAFAFB), Color(0xFFF6F6F7)),
    glassEdge = listOf(Color(0x1A000000), Color(0x1A000000)),
    heroEdge = listOf(Color(0x24000000), Color(0x24000000)),
    capsule = listOf(Color(0xFFFFFFFF), Color(0xFFFFFFFF)),
    capsulePressed = listOf(Color(0xFFEFEFF1), Color(0xFFEFEFF1)),
    // Más oscuros que en la 4.0: como texto sobre blanco pasan de 4,5:1.
    success = Color(0xFF0B7A46),
    warning = Color(0xFF8A6100),
    danger = Color(0xFFD73337),
    info = Color(0xFF63636B),
)

/** Lo que leen los componentes de Material que todavía se usan. */
private fun ZkColors.scheme(): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = surface2,
        onPrimaryContainer = text,
        inversePrimary = accent,
        secondary = surface2,
        onSecondary = text,
        secondaryContainer = surface2,
        onSecondaryContainer = text,
        tertiary = info,
        onTertiary = background,
        tertiaryContainer = surface2,
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
        scrim = Color(0x99000000),
    )

// ── Tipografía ───────────────────────────────────────────────────────────────

val InterFamily = FontFamily(
    Font(R.font.inter_light, FontWeight.Light),
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

/** Inter con cifras de ancho fijo: el saldo no baila al cambiar. */
private const val INTER_NUMBERS = "ss01, cv11, tnum"

/** Cifras de ancho fijo y cero barrado: obligatorio en datos. */
private const val MONO_FEATURES = "tnum, zero"

private fun inter(
    size: Int,
    weight: FontWeight,
    lineHeight: Int,
    tracking: Float = 0f,
    features: String = INTER_FEATURES,
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    fontFeatureSettings = features,
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

// Los títulos van apretados (−0,03 em), como en la web. Están todos los
// estilos de Material: uno sin definir caería en la fuente del sistema, que
// es lo que le pasaba al saldo del inicio.
private val ZkTypography = Typography(
    displayLarge = inter(46, FontWeight.SemiBold, 48, -2f),
    displayMedium = inter(40, FontWeight.SemiBold, 44, -1.6f),
    displaySmall = inter(36, FontWeight.SemiBold, 40, -1.2f),
    headlineLarge = inter(32, FontWeight.SemiBold, 36, -1f),
    headlineMedium = inter(28, FontWeight.SemiBold, 32, -0.8f),
    headlineSmall = inter(24, FontWeight.SemiBold, 28, -0.6f),
    titleLarge = inter(20, FontWeight.SemiBold, 26, -0.4f),
    titleMedium = inter(16, FontWeight.Medium, 22, -0.1f),
    titleSmall = inter(14, FontWeight.Medium, 18),
    bodyLarge = inter(15, FontWeight.Normal, 22),
    bodyMedium = inter(14, FontWeight.Normal, 20),
    bodySmall = inter(12, FontWeight.Normal, 17),
    labelLarge = inter(13, FontWeight.Medium, 16),
    labelMedium = inter(12, FontWeight.Medium, 15),
    labelSmall = inter(11, FontWeight.Medium, 14),
)

/**
 * Lo que Material 3 no tiene. Ninguno pone mayúsculas por su cuenta: eso lo
 * hace cada componente con `.uppercase()`.
 */
@Immutable
data class ZkTextStyles(
    /** Eyebrow de sección. 10 sp / 0,24 em. */
    val eyebrow: TextStyle,
    /** Título de panel. 10 sp / 0,2 em. */
    val panelLabel: TextStyle,
    /** Etiqueta de campo y de Stat. 10 sp / 0,18 em. */
    val fieldLabel: TextStyle,
    val pill: TextStyle,
    /** Texto de botón. 12 sp / 0,14 em. */
    val button: TextStyle,
    val mono: TextStyle,
    val monoLarge: TextStyle,
    /** Un monto destacado dentro de una tarjeta. */
    val monoDisplay: TextStyle,
    /** El saldo del inicio: Inter, cifras de ancho fijo. */
    val balance: TextStyle,
    /** Las cifras del teclado del PIN. */
    val key: TextStyle,
    /** ZOKA QUANTIC grande, como el h1 de zoka.network. */
    val brandHero: TextStyle,
    /** La marca chica en mono: `ZOKA QUANTIC` con mucho aire. */
    val brandMark: TextStyle,
    val brandTagline: TextStyle,
    /** Bajada en Inter Light, como el cuerpo de la web. */
    val lead: TextStyle,
)

private val ZkText = ZkTextStyles(
    eyebrow = mono(10, FontWeight.Medium, 14, 2.4f),
    panelLabel = mono(10, FontWeight.Medium, 14, 2f),
    fieldLabel = mono(10, FontWeight.Medium, 14, 1.8f),
    pill = mono(10, FontWeight.Medium, 14, 0.8f),
    button = mono(12, FontWeight.Medium, 16, 1.7f),
    mono = mono(13, FontWeight.Normal, 19),
    monoLarge = mono(20, FontWeight.Normal, 24),
    monoDisplay = inter(38, FontWeight.SemiBold, 42, -1.6f, INTER_NUMBERS),
    balance = inter(52, FontWeight.SemiBold, 54, -2.3f, INTER_NUMBERS),
    key = inter(26, FontWeight.Normal, 30, 0f, INTER_NUMBERS),
    brandHero = inter(46, FontWeight.SemiBold, 46, -2.1f),
    brandMark = mono(11, FontWeight.Normal, 14, 4.6f),
    brandTagline = inter(24, FontWeight.Light, 29, -0.5f),
    lead = inter(15, FontWeight.Light, 24),
)

// ── Formas y medidas ─────────────────────────────────────────────────────────
// Lo que no es cápsula (CircleShape) sale de acá.

private val ZkShapes = Shapes(
    /** Detalles: barritas, el recuadro de enfoque. */
    extraSmall = RoundedCornerShape(8.dp),
    /** Recuadros de ícono y casillas. */
    small = RoundedCornerShape(12.dp),
    /** Campos, avisos y la placa del QR. */
    medium = RoundedCornerShape(16.dp),
    /** Tarjetas. */
    large = RoundedCornerShape(20.dp),
    /** La tarjeta principal de una pantalla, hojas y diálogos. */
    extraLarge = RoundedCornerShape(28.dp),
)

@Immutable
data class ZkSpacing(
    /** Relleno de una tarjeta. */
    val panel: Dp = 16.dp,
    /** Entre tarjetas y entre bloques de una tarjeta. */
    val gap: Dp = 12.dp,
    /** Relleno vertical de una fila. */
    val row: Dp = 10.dp,
    /** Alto de campos y del botón mediano. */
    val control: Dp = 50.dp,
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
