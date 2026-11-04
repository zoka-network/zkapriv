package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.components.zkPulseAlpha
import com.zoka.zkapriv.ui.theme.Zk
import com.zoka.zkapriv.security.vault.QuanticVault
import androidx.compose.ui.draw.alpha

/**
 * Pantalla de la app: fondo del tema también bajo las barras del sistema,
 * barra superior con volver y título, y contenido que se desplaza.
 */
@Composable
fun ZkScreen(
    title: String?,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    scroll: Boolean = true,
    bottom: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Zk.colors.background)
            .statusBarsPadding()
            .let { if (LocalWalletTabs.current) it else it.navigationBarsPadding() }
            .imePadding(),
    ) {
        if (title != null || onBack != null) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "Back", tint = Zk.colors.text)
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
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
            Box(Modifier.fillMaxWidth().padding(Zk.spacing.screen)) { bottom() }
        }
    }
}

/** Seis puntos del PIN: rosa los escritos, borde los que faltan. */
@Composable
fun PinDots(filled: Int, error: Boolean = false, modifier: Modifier = Modifier) {
    val c = Zk.colors
    Row(
        modifier.semantics { contentDescription = "$filled of ${QuanticVault.PIN_LENGTH} digits" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(QuanticVault.PIN_LENGTH) { i ->
            val on = i < filled
            Box(
                Modifier
                    .size(12.dp)
                    .background(
                        when {
                            error -> c.danger
                            on -> c.accent
                            else -> c.background
                        },
                        CircleShape,
                    )
                    .border(1.dp, if (on || error) c.background else c.borderStrong, CircleShape),
            )
        }
    }
}

/**
 * Teclado numérico del PIN. `onBiometric` agrega la huella abajo a la
 * izquierda.
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
    Column(modifier.alpha(if (enabled) 1f else 0.4f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { digit -> PinKey(enabled, { onDigit(digit) }) { PinKeyText(digit.toString()) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onBiometric != null) {
                PinKey(enabled, onBiometric, "Use biometrics") {
                    Icon(Icons.Outlined.Fingerprint, null, tint = Zk.colors.accent, modifier = Modifier.size(26.dp))
                }
            } else {
                Spacer(Modifier.size(width = 84.dp, height = 60.dp))
            }
            PinKey(enabled, { onDigit('0') }) { PinKeyText("0") }
            PinKey(enabled, onDelete, "Delete") {
                Icon(Icons.Outlined.Backspace, null, tint = Zk.colors.textMuted, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun PinKey(
    enabled: Boolean,
    onClick: () -> Unit,
    description: String? = null,
    content: @Composable () -> Unit,
) {
    val c = Zk.colors
    Box(
        Modifier
            .size(width = 84.dp, height = 60.dp)
            .background(c.surface, MaterialTheme.shapes.medium)
            .border(1.dp, c.border, MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (description != null) contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PinKeyText(text: String) {
    Text(text, style = Zk.text.monoLarge, color = Zk.colors.text)
}

/** Punto de estado que late (sincronizando) o quieto. */
@Composable
fun StatusDot(on: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (on) zkPulseAlpha() else 1f
    Box(modifier.size(8.dp).alpha(alpha).background(if (on) Zk.colors.accent else Zk.colors.success, CircleShape))
}

@Composable
fun VSpace(height: Int) = Spacer(Modifier.height(height.dp))
