package com.zoka.zkapriv.ui.avatar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/** Flat, pixel-aligned squares. No images, gradients, animation or persisted bitmaps. */
@Composable
fun ZKAPrivAvatar(accountId: String, modifier: Modifier = Modifier) {
    val cells = remember(accountId) { AvatarMosaic.generate(accountId) }
    Canvas(modifier.size(40.dp).semantics {
        contentDescription = if (cells == null) "Account avatar unavailable" else "Account mosaic"
    }) {
        val side = floor(size.minDimension)
        val origin = Offset(floor((size.width - side) / 2), floor((size.height - side) / 2))
        drawRect(Color(AvatarMosaic.BACKGROUND), origin, Size(side, side))
        val padding = floor(side * 0.045f).coerceAtLeast(1f)
        val gap = floor(side * 0.014f).coerceAtLeast(1f)
        val pitch = (side - padding * 2 + gap) / AvatarMosaic.SIDE
        if (pitch <= gap) return@Canvas
        repeat(AvatarMosaic.CELL_COUNT) { index ->
            val column = index % AvatarMosaic.SIDE
            val row = index / AvatarMosaic.SIDE
            val x = floor(padding + column * pitch)
            val y = floor(padding + row * pitch)
            val right = floor(padding + (column + 1) * pitch - gap)
            val bottom = floor(padding + (row + 1) * pitch - gap)
            drawRect(
                Color(AvatarMosaic.palette[cells?.get(index) ?: 1]),
                origin + Offset(x, y), Size(right - x, bottom - y),
            )
        }
    }
}
