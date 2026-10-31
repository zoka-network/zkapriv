package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.QuanticWordmark
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bloqueo: PIN de 6 dígitos o biometría. Tras cinco fallos seguidos cada
 * intento espera más (lo cuenta [QuanticVault]). Olvidar el PIN solo se
 * arregla borrando la billetera de este teléfono y restaurándola con la frase.
 */
@Composable
fun LockScreen(vm: WalletViewModel, onUnlocked: () -> Unit, onWiped: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var waitMillis by remember { mutableLongStateOf(vm.pinWaitMillis()) }
    var forgot by remember { mutableStateOf(false) }
    val biometric = vm.biometricEnabled()

    fun askBiometric() {
        val activity = context as? FragmentActivity ?: return
        authenticate(activity, "Unlock ZKAPriv", "Confirm it is you", onSuccess = onUnlocked)
    }

    LaunchedEffect(Unit) { if (biometric && waitMillis == 0L) askBiometric() }
    LaunchedEffect(waitMillis) {
        if (waitMillis > 0) {
            delay(1000)
            waitMillis = vm.pinWaitMillis()
        }
    }
    LaunchedEffect(entry) {
        if (entry.length < QuanticVault.PIN_LENGTH) return@LaunchedEffect
        when (val check = vm.checkPin(entry)) {
            QuanticVault.PinCheck.Ok -> onUnlocked()
            is QuanticVault.PinCheck.Wrong -> message =
                if (check.attemptsLeft > 0) "Wrong PIN. ${check.attemptsLeft} tries before a wait."
                else "Wrong PIN."
            is QuanticVault.PinCheck.Wait -> {
                message = "Too many wrong PINs."
                waitMillis = check.millis
            }
        }
        entry = ""
    }

    ZkScreen(title = null, scroll = false) {
        Spacer(Modifier.weight(0.6f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            QuanticWordmark(MaterialTheme.typography.headlineMedium)
            VSpace(8)
            Text("Enter your PIN", style = MaterialTheme.typography.bodyLarge, color = Zk.colors.textMuted)
            VSpace(24)
            PinDots(entry.length, error = message != null && entry.isEmpty())
            VSpace(12)
            when {
                waitMillis > 0 -> HintText("Try again in ${(waitMillis + 999) / 1000} s")
                message != null -> ErrorText(message!!)
                else -> VSpace(17)
            }
        }
        Spacer(Modifier.weight(0.4f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            PinPad(
                onDigit = { digit ->
                    message = null
                    if (entry.length < QuanticVault.PIN_LENGTH) entry += digit
                },
                onDelete = { entry = entry.dropLast(1) },
                onBiometric = if (biometric) ({ askBiometric() }) else null,
                enabled = waitMillis == 0L,
            )
            VSpace(8)
            ZButton({ forgot = !forgot }, variant = ZButtonVariant.Ghost) { ZButtonText("Forgot your PIN?") }
        }
        if (forgot) {
            Notice(
                "The PIN only protects this phone. To set a new one, remove the wallet from this phone " +
                    "and restore it with your 24 words. Without the words, the coins are lost.",
                title = "Remove and restore",
                tone = ZkTone.Err,
                action = {
                    ZButton(
                        onClick = { scope.launch { vm.wipe(); onWiped() } },
                        variant = ZButtonVariant.Danger,
                    ) { ZButtonText("Remove wallet from this phone") }
                },
            )
        }
        VSpace(8)
    }
}

