package com.zoka.zkapriv.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.components.CopyButton
import com.zoka.zkapriv.ui.components.CopyableKeyValueRow
import com.zoka.zkapriv.ui.components.EmptyState
import com.zoka.zkapriv.ui.components.Eyebrow
import com.zoka.zkapriv.ui.components.KeyValueRow
import com.zoka.zkapriv.ui.components.MeterBar
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.QuanticWordmark
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.Segmented
import com.zoka.zkapriv.ui.components.Stat
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZInput
import com.zoka.zkapriv.ui.components.ZLabel
import com.zoka.zkapriv.ui.components.ZToggle
import com.zoka.zkapriv.ui.components.ZkQrCode
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk
import com.zoka.zkapriv.ui.theme.ZkTheme

private const val SAMPLE_SHORT = "zqtest2qyf7m0wd5x9l3c8r2n6h4k1j0p5s9t3v7w2e6u8a4z0y5m3"
private const val SAMPLE_VIEW_KEY =
    "zqtestview21qpzry9x8gf2tvdw0s3jn54khce6mua7lqpzry9x8gf2tvdw0s3jn54khce6mua7l"

/**
 * Todos los componentes en un tema, para mirarlos (y sacarles captura) sin
 * pasar por la billetera. Solo existe en las compilaciones de depuración:
 * `adb shell am start -n <paquete>/com.zoka.zkapriv.MainActivity --es screen gallery`.
 */
@Composable
fun ZkGallery(dark: Boolean, onToggleTheme: () -> Unit) {
    ZkTheme(darkTheme = dark) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Zk.colors.background)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(Zk.spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Zk.spacing.gap),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuanticWordmark(MaterialTheme.typography.titleLarge, suffix = "TESTNET")
                ZToggle(checked = dark, onCheckedChange = { onToggleTheme() }, description = "Dark theme")
            }
            QuanticWordmark(Zk.text.brandHero)
            Text("Private by design.", style = Zk.text.brandTagline, color = Zk.colors.textMuted)
            SectionTitle("Gallery", "ZKAPriv 3 components", "Every primitive, in the current theme.")

            Panel(title = "Buttons") {
                ZButton(onClick = {}, Modifier.fillMaxWidth(), variant = ZButtonVariant.Primary, size = ZButtonSize.Large) {
                    ZButtonText("Send ZKA")
                }
                ZButton(onClick = {}, Modifier.fillMaxWidth(), variant = ZButtonVariant.Accent) {
                    ZButtonText("Create wallet")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZButton(onClick = {}, Modifier.weight(1f)) { ZButtonText("Default") }
                    ZButton(onClick = {}, Modifier.weight(1f), variant = ZButtonVariant.Outline) { ZButtonText("Outline") }
                    ZButton(onClick = {}, Modifier.weight(1f), variant = ZButtonVariant.Ghost) { ZButtonText("Ghost") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ZButton(onClick = {}, Modifier.weight(1f), variant = ZButtonVariant.Danger) { ZButtonText("Delete") }
                    ZButton(onClick = {}, Modifier.weight(1f), enabled = false, variant = ZButtonVariant.Primary) {
                        ZButtonText("Off")
                    }
                    CopyButton(SAMPLE_SHORT, Modifier.weight(1f))
                }
            }

            Panel(title = "Status") {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Testnet", tone = ZkTone.Accent)
                    Pill("Synced", tone = ZkTone.On)
                    Pill("Syncing", tone = ZkTone.Warn)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill("Offline", tone = ZkTone.Err)
                    Pill("Tor", tone = ZkTone.Info)
                    Pill("Off", tone = ZkTone.Off)
                    Pill("Default")
                }
                MeterBar(0.62f)
                Notice("Payments are slower while Tor mode is on.", title = "Tor mode", tone = ZkTone.Info)
                Notice("Test coins, no value.", tone = ZkTone.Warn)
                Notice("The node refused the payment: its anchor expired.", title = "Not sent", tone = ZkTone.Err)
            }

            Panel(title = "Balance", right = { Pill("Synced", tone = ZkTone.On) }) {
                Eyebrow("Spendable")
                Text("1,284.500000", style = Zk.text.monoDisplay, color = Zk.colors.text)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Stat("Immature", "24.000000", hint = "Mined, unlocks in 41 blocks")
                    Stat("Height", "1,142", tone = ZkTone.Accent)
                }
                ThinDivider()
                KeyValueRow("Fee", "0.000200 ZKA")
                CopyableKeyValueRow("Short address", SAMPLE_SHORT)
                CopyableKeyValueRow("View key", SAMPLE_VIEW_KEY)
            }

            Panel(title = "Inputs") {
                var to by remember { mutableStateOf("") }
                var amount by remember { mutableStateOf("12.5") }
                var memo by remember { mutableStateOf("") }
                var unit by remember { mutableStateOf("ZKA") }
                var tor by remember { mutableStateOf(true) }
                ZLabel("Recipient")
                ZInput(to, { to = it }, Modifier.fillMaxWidth(), placeholder = "zqtest2… short or full address")
                ZLabel("Amount")
                ZInput(amount, { amount = it }, Modifier.fillMaxWidth(), isError = amount == "0")
                ZLabel("Memo")
                ZInput(memo, { memo = it }, Modifier.fillMaxWidth(), placeholder = "Optional, up to 512 bytes", mono = false, singleLine = false)
                Segmented(listOf("ZKA", "Atoms"), unit, { unit = it }, Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Tor mode", style = MaterialTheme.typography.bodyLarge, color = Zk.colors.text)
                    ZToggle(tor, { tor = it }, description = "Tor mode")
                }
            }

            Panel(title = "Receive") {
                ZkQrCode(SAMPLE_SHORT, Modifier.align(Alignment.CenterHorizontally))
            }

            EmptyState("No movements yet", sub = "Payments you send or receive show up here.")
        }
    }
}

