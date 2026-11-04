package com.zoka.zkapriv.ui.quantic

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.components.CopyButton
import com.zoka.zkapriv.ui.components.CopyableKeyValueRow
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.Segmented
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZkQrCode
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.qrscanner.MultiPartQr
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recibir: la dirección corta (57 caracteres, un QR común) para compartir, y
 * la completa (unos 1760) en un QR de varias partes que ZSilent y ZKAPriv leen.
 * La corta sirve cuando la red conoce la completa: se publica una vez.
 */
@Composable
fun ReceiveScreen(vm: WalletViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val short = remember { vm.shortAddress() }
    val full = remember { vm.fullAddress() }
    var which by remember { mutableStateOf("Short") }
    val state by vm.state.collectAsState()
    val published = state.addressPublished
    var publishing by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }

    fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, "Share address"))
    }

    ZkScreen(title = "Receive ZKA") {
        Segmented(listOf("Short", "Full"), which, { which = it }, Modifier.fillMaxWidth())
        if (which == "Short") {
            Panel {
                ZkQrCode(short, Modifier.align(Alignment.CenterHorizontally))
                CopyableKeyValueRow("Short address", short)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CopyButton(short, Modifier.weight(1f))
                    ZButton({ share(short) }, Modifier.weight(1f), size = ZButtonSize.Small) { ZButtonText("Share") }
                }
            }
            Panel(
                title = "Directory",
                right = {
                    when (published) {
                        true -> Pill("Published", tone = ZkTone.On)
                        false -> Pill("Not published", tone = ZkTone.Warn)
                    }
                },
            ) {
                Text(
                    "Wallets find your full address from the short one in the network's directory. " +
                        "Publish it once so people can pay the short address.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = Zk.colors.textMuted,
                )
                ZButton(
                    onClick = {
                        publishing = true
                        publishError = null
                        scope.launch {
                            vm.publishAddress()
                                .onSuccess { if (!it) publishError = "The network has not accepted your address yet. Try again." }
                                .onFailure { publishError = "Could not publish your address. Try again." }
                            publishing = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ZButtonVariant.Accent,
                    enabled = !publishing && !published,
                ) { ZButtonText(if (publishing) "Publishing…" else if (published) "Address published" else "Publish address") }
                publishError?.let { Notice(it, tone = ZkTone.Err) }
            }
        } else {
            Panel {
                AnimatedMultiPartQr(full)
                CopyableKeyValueRow("Full address", full)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CopyButton(full, Modifier.weight(1f))
                    ZButton({ share(full) }, Modifier.weight(1f), size = ZButtonSize.Small) { ZButtonText("Share") }
                }
            }
            HintText("Works without the directory. Scan it with ZKAPriv or ZSilent: the parts can come in any order.")
        }
    }
}

/** Las partes del QR en ciclo; sin animaciones, se pasan a mano. */
@Composable
private fun AnimatedMultiPartQr(text: String) {
    val parts = remember(text) { MultiPartQr.split(text) }
    var index by remember { mutableIntStateOf(0) }
    val animate = Zk.animations
    LaunchedEffect(parts, animate) {
        while (animate && parts.size > 1) {
            delay(500)
            index = (index + 1) % parts.size
        }
    }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ZkQrCode(parts[index])
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!animate && parts.size > 1) {
                ZButton({ index = (index + parts.size - 1) % parts.size }, size = ZButtonSize.Small) { ZButtonText("Previous") }
            }
            Text("Part ${index + 1} of ${parts.size}", style = Zk.text.mono, color = Zk.colors.textMuted)
            if (!animate && parts.size > 1) {
                ZButton({ index = (index + 1) % parts.size }, size = ZButtonSize.Small) { ZButtonText("Next") }
            }
        }
    }
}
