package com.zoka.zkapriv.ui.quantic

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.BrandMark
import com.zoka.zkapriv.ui.components.GlassArc
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonVariant

/**
 * Bloqueo: PIN de 6 dígitos o biometría. Tras cinco fallos seguidos cada
 * intento espera más (lo cuenta [QuanticVault]). Olvidar el PIN se arregla en
 * Reset: sacar la billetera de este teléfono y restaurarla con la frase.
 */
@Composable
fun LockScreen(vm: WalletViewModel, onUnlocked: () -> Unit, onForgot: () -> Unit) {
    val context = LocalContext.current
    val biometric = remember { vm.biometricEnabled() }

    fun askBiometric() {
        val activity = context as? FragmentActivity ?: return
        authenticate(activity, "Unlock ZKAPriv", "Confirm it is you", onSuccess = onUnlocked)
    }

    LaunchedEffect(Unit) { if (biometric && vm.pinWaitMillis() == 0L) askBiometric() }
    LockContent(
        check = vm::checkPin,
        waitMillis = vm::pinWaitMillis,
        onUnlocked = onUnlocked,
        onBiometric = if (biometric) ({ askBiometric() }) else null,
        onForgot = onForgot,
    )
}

/** El bloqueo sin el almacén: también lo dibuja la vista de diseño. */
@Composable
internal fun LockContent(
    check: (String) -> QuanticVault.PinCheck,
    waitMillis: () -> Long,
    onUnlocked: () -> Unit,
    onBiometric: (() -> Unit)?,
    onForgot: () -> Unit,
) {
    ZkScreen(title = null, scroll = false, backdrop = { GlassArc() }) {
        PinPrompt(
            title = "Enter your PIN",
            check = check,
            waitMillis = waitMillis,
            onOk = onUnlocked,
            onBiometric = onBiometric,
            header = { BrandMark() },
            footer = {
                ZButton(onForgot, variant = ZButtonVariant.Ghost) {
                    Text("Forgot your PIN?", style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current)
                }
            },
        )
    }
}
