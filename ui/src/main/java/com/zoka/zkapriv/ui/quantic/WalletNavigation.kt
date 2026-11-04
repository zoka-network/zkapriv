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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.theme.Zk

internal val LocalWalletTabs = staticCompositionLocalOf { false }

/** Las pestañas de abajo: Wallet, Send, Receive, Activity y Settings. */
internal val walletRoutes = setOf("home", "send", "receive", "history", "settings")

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
    Column(Modifier.background(c.background).navigationBarsPadding()) {
        ThinDivider()
        Row(Modifier.fillMaxWidth().height(72.dp)) {
            tabs.forEach { (destination, title, icon) ->
                val selected = route == destination
                val tint = if (selected) c.accent else c.textMuted
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(destination) }
                        .semantics {
                            role = Role.Tab
                            this.selected = selected
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(title, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .width(40.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (selected) c.accent else Color.Transparent),
                    )
                }
            }
        }
    }
}
