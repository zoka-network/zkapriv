package com.zoka.zkapriv.ui.quantic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZkTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Sacar las billeteras de este teléfono. Es la salida cuando se olvidó el PIN
 * (se llega desde el bloqueo) y la «Danger zone» de los ajustes: una sola
 * pantalla para las dos. Solo las 24 palabras de cada una la devuelven.
 */
@Composable
fun ResetScreen(vm: WalletViewModel, onBack: () -> Unit, onWiped: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sending by vm.sendState.collectAsState()
    var removing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ResetContent(
        sending = sending.running,
        removing = removing,
        error = error,
        onBack = onBack,
        onRemove = {
            removing = true
            error = null
            scope.launch {
                try {
                    vm.wipe()
                    onWiped()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "Could not remove the wallets. Try again."
                    removing = false
                }
            }
        },
    )
}

/** Sin estado de la billetera: también la dibuja la vista de diseño. */
@Composable
internal fun ResetContent(
    sending: Boolean,
    removing: Boolean,
    error: String?,
    onBack: () -> Unit,
    onRemove: () -> Unit,
) {
    var hasWords by remember { mutableStateOf(false) }
    BackHandler(enabled = removing) { }
    ZkScreen(
        title = null,
        onBack = { if (!removing) onBack() },
        bottom = {
            ZButton(
                onRemove,
                Modifier.fillMaxWidth(),
                ZButtonVariant.Danger,
                ZButtonSize.Large,
                enabled = hasWords && !sending && !removing,
            ) { ZButtonText(if (removing) "Removing…" else "Remove from this phone") }
        },
    ) {
        SectionTitle(
            null,
            "Start again on this phone",
            "The PIN only protects this phone. To choose a new one, remove the wallets from here and " +
                "restore each one with its 24 words.",
        )
        Notice(
            "Without its 24 words, a wallet and its coins are lost. Check you have them on paper first.",
            title = "You need your 24 words",
            tone = ZkTone.Err,
        )
        if (sending) {
            Notice("Wait until your payment finishes before removing the wallets.", tone = ZkTone.Warn)
        }
        Panel(contentPadding = PaddingValues(0.dp), gap = 0.dp) {
            SwitchRow("I have the 24 words of every wallet", null, hasWords, Icons.Outlined.Key, enabled = !removing) { hasWords = it }
        }
        HintText(
            "This removes every wallet and setting from ZKAPriv on this phone. Nothing changes on the network.",
            Modifier.padding(horizontal = 4.dp),
        )
        error?.let { ErrorText(it) }
    }
}
