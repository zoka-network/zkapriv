package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.components.zkGlass
import com.zoka.zkapriv.ui.components.zkInk
import com.zoka.zkapriv.ui.theme.Zk

internal val LocalWalletTabs = staticCompositionLocalOf { false }

/** Las pestañas de abajo: Wallet, Send, Receive, Activity y Settings. */
internal val walletRoutes = setOf("home", "send", "receive", "history", "settings")

/**
 * La barra inferior: una cápsula de vidrio con las cinco pestañas. La activa
 * es negra con ícono y texto rosa, como los botones. Las etiquetas van en
 * Inter 11 sp sin tracking: a 360 dp con la letra al 110 % las mayúsculas con
 * aire no entraban.
 */
@Composable
internal fun WalletNavigation(route: String, onSelect: (String) -> Unit) {
    val c = Zk.colors
    val tabs = listOf(
        Triple("home", "Wallet", Icons.Outlined.AccountBalanceWallet),
        Triple("send", "Send", Icons.Outlined.CallMade),
        Triple("receive", "Receive", Icons.Outlined.CallReceived),
        Triple("history", "Activity", Icons.Outlined.History),
        Triple("settings", "Settings", Icons.Outlined.Settings),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.background)
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(66.dp).zkGlass(CircleShape).padding(5.dp)) {
            tabs.forEach { (destination, title, icon) ->
                val selected = route == destination
                val tint = if (selected) c.onInk else c.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (selected) Modifier.zkInk() else Modifier)
                        .clip(CircleShape)
                        .clickable { onSelect(destination) }
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(title, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}
