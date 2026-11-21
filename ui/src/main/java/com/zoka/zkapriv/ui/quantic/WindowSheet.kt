package com.zoka.zkapriv.ui.quantic

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.theme.ZK_DURATION_ENTER
import com.zoka.zkapriv.ui.theme.Zk
import com.zoka.zkapriv.ui.theme.ZkEasing
import kotlinx.coroutines.launch

/**
 * Una ventana que sube desde abajo, sobre lo que haya (también sobre la barra).
 * Arriba a la izquierda maximiza (y vuelve a media altura); a la derecha
 * minimiza, que la baja y la cierra. También se arrastra desde el encabezado:
 * hacia arriba la maximiza, hacia abajo la achica o la cierra. Atrás y tocar
 * afuera también la cierran.
 */
@Composable
internal fun WindowSheet(
    title: String,
    onDismiss: () -> Unit,
    half: Float = 0.62f,
    startFull: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Zk.colors
    val animate = Zk.animations
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val topInset = WindowInsets.statusBars.getTop(density).toFloat()
    BoxWithConstraints(Modifier.fillMaxSize().testTag("window-sheet")) {
        val screen = with(density) { maxHeight.toPx() }
        val fullPx = screen - topInset
        val halfPx = (screen * half).coerceAtMost(fullPx)
        var maximized by rememberSaveable { mutableStateOf(startFull) }
        var dragging by remember { mutableStateOf(false) }
        val height = remember { Animatable(if (maximized) fullPx else halfPx) }
        val shown = remember { Animatable(if (animate) 0f else 1f) }
        var closing by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { if (animate) shown.animateTo(1f, tween(ZK_DURATION_ENTER, easing = ZkEasing)) }
        // El margen de la barra de estado puede llegar después del primer cuadro: el alto se rehace.
        LaunchedEffect(fullPx, halfPx) { if (!dragging && !height.isRunning) height.snapTo(if (maximized) fullPx else halfPx) }
        fun settle(full: Boolean) = scope.launch {
            maximized = full
            val target = if (full) fullPx else halfPx
            if (animate) height.animateTo(target, tween(ZK_DURATION_ENTER, easing = ZkEasing)) else height.snapTo(target)
        }
        fun close() {
            if (closing) return
            closing = true
            scope.launch {
                if (animate) shown.animateTo(0f, tween(ZK_DURATION_ENTER, easing = ZkEasing))
                onDismiss()
            }
        }
        BackHandler { close() }

        // Lo de atrás se oscurece; tocarlo cierra la hoja.
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.background(Color.Black.copy(alpha = 0.6f))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = ::close))

        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .graphicsLayer { translationY = (1f - shown.value) * size.height }
                .background(c.elevated, shape)
                .border(BorderStroke(1.dp, Brush.verticalGradient(c.heroEdge)), shape)
                .clip(shape)
                // Tocar la hoja no la cierra.
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = {}),
        ) {
            Column(
                Modifier.fillMaxWidth().draggable(
                    rememberDraggableState { delta ->
                        scope.launch { height.snapTo((height.value - delta).coerceIn(halfPx * 0.35f, fullPx)) }
                    },
                    Orientation.Vertical,
                    onDragStarted = { dragging = true },
                    onDragStopped = { velocity ->
                        dragging = false
                        val now = height.value
                        when {
                            velocity > 2400f || now < halfPx * 0.72f -> close()
                            velocity < -2400f || now > (halfPx + fullPx) / 2 -> settle(true)
                            else -> settle(false)
                        }
                    },
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.padding(top = 8.dp).size(width = 36.dp, height = 4.dp).background(c.textMuted.copy(alpha = 0.5f), CircleShape))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    SheetButton(
                        if (maximized) Icons.Outlined.CloseFullscreen else Icons.Outlined.OpenInFull,
                        if (maximized) "Restore" else "Maximize",
                        Modifier.testTag("sheet-maximize"),
                    ) { settle(!maximized) }
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = c.text,
                        textAlign = TextAlign.Center, maxLines = 1)
                    SheetButton(Icons.Outlined.Remove, "Minimize", Modifier.testTag("sheet-minimize")) { close() }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Zk.spacing.screen)
                    .padding(top = 4.dp, bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(Zk.spacing.gap),
                content = content,
            )
        }
    }
}

@Composable
private fun SheetButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Zk.colors.text, modifier = Modifier.size(22.dp))
    }
}
