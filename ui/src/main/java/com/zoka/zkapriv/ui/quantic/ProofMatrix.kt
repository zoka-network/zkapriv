package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay
import kotlin.random.Random

internal enum class ProofResult { Working, Accepted, Failed }

/** Actividad visual, sin porcentaje ficticio ni datos de la prueba. */
@Composable
internal fun ProofMatrix(result: ProofResult, modifier: Modifier = Modifier) {
    val colors = Zk.colors
    val animate = Zk.animations && result == ProofResult.Working
    val random = remember { Random(System.nanoTime()) }
    var cells by remember { mutableStateOf(List(256) { if (it % 7 == 0) .65f else .07f }) }
    LaunchedEffect(animate) {
        while (animate) {
            cells = cells.map { previous ->
                if (random.nextFloat() < .08f) random.nextFloat() * .45f + .35f
                else (previous * .68f).coerceAtLeast(.07f)
            }
            delay(160)
        }
    }
    val ink = if (result == ProofResult.Failed) colors.danger else colors.accent
    val description = when (result) {
        ProofResult.Working -> "Preparing a private payment"
        ProofResult.Accepted -> "Payment accepted by the network"
        ProofResult.Failed -> "The payment could not be confirmed"
    }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.widthIn(max = 264.dp).fillMaxWidth().aspectRatio(1f)
                .border(1.dp, ink.copy(alpha = .30f), RoundedCornerShape(10.dp))
                .background(colors.surface2, RoundedCornerShape(10.dp))
                .padding(14.dp).semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val pitch = size.width / 16f
                val side = pitch * .66f
                cells.forEachIndexed { index, alpha ->
                    drawRect(ink.copy(alpha = if (result == ProofResult.Working) alpha else .14f),
                        topLeft = Offset(index % 16 * pitch + (pitch - side) / 2, index / 16 * pitch + (pitch - side) / 2),
                        size = Size(side, side))
                }
            }
            if (result != ProofResult.Working) {
                Icon(if (result == ProofResult.Accepted) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null, tint = ink,
                    modifier = Modifier.size(112.dp).background(colors.surface2, RoundedCornerShape(12.dp)).padding(12.dp))
            }
        }
    }
}
