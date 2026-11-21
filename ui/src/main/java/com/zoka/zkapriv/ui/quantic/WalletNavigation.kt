package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.theme.Zk

internal val LocalWalletTabs = staticCompositionLocalOf { false }
internal val walletRoutes = setOf("home", "send", "receive", "history", "settings")

/**
 * Cinco accesos. Receive y Network (el anillo de la red) no cambian de
 * pantalla: abren una hoja encima. Settings se abre desde el engranaje de Home.
 */
@Composable
internal fun WalletNavigation(route: String, onSelect: (String) -> Unit) {
    val c = Zk.colors
    val tabs = listOf(
        "home" to "Wallet",
        "receive" to "Receive",
        "send" to "Send",
        "network" to "Network",
        "history" to "Activity",
    )
    Row(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding()
        .padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        tabs.forEach { (destination, title) ->
            Box(Modifier.size(56.dp).clip(CircleShape).clickable { onSelect(destination) }
                .semantics { role = Role.Tab; selected = route == destination; contentDescription = title },
                contentAlignment = Alignment.Center) {
                DashboardNavIcon(destination, c.text, Modifier.size(28.dp))
            }
        }
    }
}

/** Trazos locales como los de la referencia, sin recursos descargados. */
@Composable
internal fun DashboardNavIcon(route: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        scale(size.width / 32f, size.height / 32f, pivot = Offset.Zero) {
            val stroke = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (route) {
                "home" -> {
                    drawRoundRect(color, Offset(2f, 8f), Size(28f, 21f), CornerRadius(4f), style = stroke)
                    drawPath(Path().apply {
                        moveTo(3f, 9f); lineTo(3f, 7f); quadraticBezierTo(3f, 4f, 6f, 4f)
                        lineTo(24f, 2f); quadraticBezierTo(27f, 2f, 27f, 5f); lineTo(27f, 8f)
                    }, color, style = stroke)
                    drawCircle(color, 2f, Offset(24f, 18f))
                }
                "receive", "send" -> {
                    drawCircle(color, 13f, Offset(16f, 16f), style = stroke)
                    val down = route == "receive"
                    drawLine(color, Offset(16f, 9f), Offset(16f, 23f), 2.2f, StrokeCap.Round)
                    drawPath(Path().apply {
                        moveTo(10f, if (down) 17f else 15f)
                        lineTo(16f, if (down) 23f else 9f)
                        lineTo(22f, if (down) 17f else 15f)
                    }, color, style = stroke)
                }
                // El anillo de la red, en chico: tres arcos y uno tenue, con el centro.
                "network" -> {
                    val ring = Stroke(2.6f, cap = StrokeCap.Round)
                    listOf(225f to 80f, 330f to 50f, 112f to 90f).forEach { (start, sweep) ->
                        drawArc(color, start, sweep, false, Offset(3f, 3f), Size(26f, 26f), style = ring)
                    }
                    drawArc(color.copy(alpha = 0.35f), 42f, 28f, false, Offset(3f, 3f), Size(26f, 26f), style = ring)
                    drawCircle(color, 6.5f, Offset(16f, 16f), style = Stroke(1.6f))
                }
                else -> listOf(7f, 16f, 25f).forEach { y ->
                    drawCircle(color, 1.7f, Offset(4f, y))
                    drawLine(color, Offset(12f, y), Offset(29f, y), 2.5f, StrokeCap.Round)
                }
            }
        }
    }
}
