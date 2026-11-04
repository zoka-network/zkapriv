package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CallMade
import androidx.compose.material.icons.outlined.CallReceived
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.theme.Zk

internal val LocalWalletTabs = staticCompositionLocalOf { false }
internal val walletRoutes = setOf("home", "send", "receive", "history")

@Composable
internal fun WalletNavigation(route: String, onSelect: (String) -> Unit) {
    val tabs = listOf(
        Triple("home", "Home", Icons.Outlined.AccountBalanceWallet),
        Triple("send", "Send", Icons.Outlined.CallMade),
        Triple("receive", "Receive", Icons.Outlined.CallReceived),
        Triple("history", "Activity", Icons.Outlined.History),
    )
    Column(Modifier.background(Zk.colors.background).navigationBarsPadding()) {
        ThinDivider()
        NavigationBar(containerColor = Zk.colors.background, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
            tabs.forEach { (destination, title, icon) ->
                NavigationBarItem(
                    selected = route == destination,
                    onClick = { onSelect(destination) },
                    icon = { Icon(icon, contentDescription = null) },
                    label = { Text(title) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Zk.colors.accent,
                        selectedTextColor = Zk.colors.accent,
                        indicatorColor = Zk.colors.surface2,
                        unselectedIconColor = Zk.colors.textMuted,
                        unselectedTextColor = Zk.colors.textMuted,
                    ),
                )
            }
        }
    }
}
