package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.network.quantic.QuanticWallet.Companion.formatZka
import com.zoka.zkapriv.network.quantic.QuanticWallet.Companion.parseZka
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.CopyableKeyValueRow
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.Eyebrow
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.KeyValueRow
import com.zoka.zkapriv.ui.components.MeterBar
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZInput
import com.zoka.zkapriv.ui.components.ZLabel
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.components.middleTruncated
import com.zoka.zkapriv.ui.components.zkPulseAlpha
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class SendStep { Form, Review, Pin, Working, Done, Failed }

/** Lo que trae un QR o un enlace: `zoka:<dirección>?amount=<ZKA>` o la dirección sola. */
fun parsePaymentText(text: String): Pair<String, String?> {
    val body = text.trim().removePrefix("zoka:")
    val address = body.substringBefore('?')
    val amount = body.substringAfter('?', "")
        .split('&')
        .firstOrNull { it.startsWith("amount=") }
        ?.removePrefix("amount=")
    return address to amount
}

@Composable
fun SendScreen(
    vm: WalletViewModel,
    scanned: String?,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var step by remember { mutableStateOf(SendStep.Form) }
    var to by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    var result by remember { mutableStateOf<JSONObject?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(scanned) {
        if (scanned != null) {
            val (address, amount) = parsePaymentText(scanned)
            to = address
            if (amount != null && parseZka(amount) != null) amountText = amount
        }
    }

    val kind = remember(to) {
        if (to.isBlank()) null else runCatching { vm.checkAddress(to.trim()) }.getOrNull()
            ?.takeIf { it.optBoolean("valid") }?.getString("kind")
    }
    val amount = parseZka(amountText)
    val memoBytes = memo.toByteArray(Charsets.UTF_8).size
    LaunchedEffect(amount) {
        quote = null
        if (amount != null && amount > 0) {
            delay(250)
            quote = runCatching { vm.quote(amount) }.getOrNull()
        }
    }
    val enough = quote?.optBoolean("enough") == true
    val fee = quote?.optLong("fee_atoms") ?: 0L

    fun launchSend() {
        step = SendStep.Working
        startedAt = System.currentTimeMillis()
        scope.launch {
            runCatching { vm.startSend(to.trim(), amount!!, memo).await() }
                .onSuccess {
                    result = it
                    step = SendStep.Done
                }
                .onFailure {
                    failure = it.message ?: "The payment was not sent"
                    step = SendStep.Failed
                }
        }
    }

    fun authorize() {
        val activity = context as? FragmentActivity
        if (vm.biometricEnabled() && activity != null) {
            authenticate(activity, "Send ${formatZka(amount ?: 0)} ZKA", "Confirm it is you", onSuccess = ::launchSend)
        } else {
            pin = ""
            pinError = null
            step = SendStep.Pin
        }
    }

    when (step) {
        SendStep.Form -> ZkScreen(
            title = "Send ZKA",
            onBack = onBack,
            bottom = {
                ZButton(
                    onClick = { step = SendStep.Review },
                    modifier = Modifier.fillMaxWidth(),
                    variant = ZButtonVariant.Primary,
                    size = ZButtonSize.Large,
                    enabled = kind != null && amount != null && amount > 0 && enough && memoBytes <= 512 &&
                        state.pendingTxid == null,
                ) { ZButtonText("Review") }
            },
        ) {
            if (state.pendingTxid != null) {
                Notice(
                    "Wait until your last payment is mined (or expires) to send another.",
                    title = "A payment is pending",
                    tone = ZkTone.Warn,
                )
            }
            ZLabel("To")
            ZInput(
                value = to,
                onValueChange = { to = it.trim() },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "zqtest2… short or full address",
                isError = to.isNotBlank() && kind == null,
                trailing = {
                    Row {
                        IconButton(onClick = { clipboard.getText()?.text?.let { to = parsePaymentText(it).first } }) {
                            Icon(Icons.Outlined.ContentPaste, "Paste", tint = Zk.colors.textMuted)
                        }
                        IconButton(onClick = onScan) {
                            Icon(Icons.Outlined.QrCodeScanner, "Scan", tint = Zk.colors.accent)
                        }
                    }
                },
            )
            when {
                to.isNotBlank() && kind == null -> ErrorText("Not a Zoka Quantic testnet address.")
                kind == "short" -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Short", tone = ZkTone.Accent)
                    HintText("The network finds the full address when you send.")
                }
                kind == "full" -> Pill("Full address", tone = ZkTone.On)
            }
            VSpace(4)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ZLabel("Amount", Modifier.weight(1f))
                ZButton(
                    onClick = { amountText = formatZka(state.maxSendableAtoms).replace(",", "") },
                    variant = ZButtonVariant.Ghost,
                    size = ZButtonSize.Small,
                    enabled = state.maxSendableAtoms > 0,
                ) { ZButtonText("Max") }
            }
            ZInput(
                value = amountText,
                onValueChange = { amountText = it.replace(',', '.') },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "0.000000",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = amountText.isNotBlank() && (amount == null || (quote != null && !enough)),
                trailing = { Text("ZKA", style = Zk.text.mono, color = Zk.colors.accent) },
            )
            when {
                amountText.isNotBlank() && amount == null -> ErrorText("Up to 6 decimals.")
                quote != null && !enough -> ErrorText(quote!!.optString("reason", "Not enough mature funds."))
                quote != null -> HintText("Fee ${formatZka(fee)} ZKA · spendable ${formatZka(state.spendableAtoms)} ZKA")
                else -> HintText("Spendable ${formatZka(state.spendableAtoms)} ZKA")
            }
            VSpace(4)
            ZLabel("Memo")
            ZInput(
                value = memo,
                onValueChange = { memo = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = "Optional. Only the recipient can read it.",
                mono = false,
                singleLine = false,
                isError = memoBytes > 512,
            )
            HintText("$memoBytes / 512 bytes")
        }

        SendStep.Review -> ZkScreen(
            title = "Review",
            onBack = { step = SendStep.Form },
            bottom = {
                ZButton(::authorize, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large) {
                    ZButtonText("Send")
                }
            },
        ) {
            Panel {
                Eyebrow("You send")
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatZka(amount ?: 0), style = Zk.text.monoDisplay, color = Zk.colors.text)
                    Text("ZKA", style = Zk.text.mono, color = Zk.colors.accent)
                }
                ThinDivider()
                KeyValueRow("To", middleTruncated(to.trim(), 14, 10))
                KeyValueRow("Network fee", formatZka(fee) + " ZKA")
                KeyValueRow("Total", formatZka((amount ?: 0) + fee) + " ZKA")
                if (memo.isNotBlank()) KeyValueRow("Memo", memo)
            }
            Notice(
                "This phone builds the zero-knowledge proof itself, and that takes a while. Keep ZKAPriv open " +
                    "until it is sent." + if (vm.torMode()) " Tor mode is on: sending goes through Tor." else "",
                title = "Proof on this phone",
                tone = ZkTone.Info,
            )
        }

        SendStep.Pin -> ZkScreen(title = "Confirm with PIN", onBack = { step = SendStep.Review }, scroll = false) {
            LaunchedEffect(pin) {
                if (pin.length == QuanticVault.PIN_LENGTH) {
                    when (val check = vm.checkPin(pin)) {
                        QuanticVault.PinCheck.Ok -> launchSend()
                        is QuanticVault.PinCheck.Wrong -> pinError = "Wrong PIN."
                        is QuanticVault.PinCheck.Wait ->
                            pinError = "Too many wrong PINs. Wait ${(check.millis + 999) / 1000} s."
                    }
                    pin = ""
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PinDots(pin.length, error = pinError != null && pin.isEmpty())
                VSpace(12)
                pinError?.let { ErrorText(it) }
                VSpace(20)
                PinPad(
                    onDigit = {
                        pinError = null
                        if (pin.length < QuanticVault.PIN_LENGTH) pin += it
                    },
                    onDelete = { pin = pin.dropLast(1) },
                )
            }
            VSpace(12)
        }

        SendStep.Working -> ZkScreen(title = null, scroll = false) {
            var elapsed by remember { mutableLongStateOf(0L) }
            LaunchedEffect(startedAt) {
                while (true) {
                    elapsed = (System.currentTimeMillis() - startedAt) / 1000
                    delay(1000)
                }
            }
            Spacer(Modifier.weight(1f))
            SectionTitle(
                "Sending",
                "Proving on this phone…",
                "The proof shows the network that the payment is valid without showing who pays whom or how much.",
            )
            VSpace(8)
            MeterBar(zkPulseAlpha(), Modifier.fillMaxWidth())
            Text("%d:%02d".format(elapsed / 60, elapsed % 60), style = Zk.text.mono, color = Zk.colors.textMuted)
            Spacer(Modifier.weight(1f))
            HintText("Keep ZKAPriv open. Do not lock the phone.")
            VSpace(12)
        }

        SendStep.Done -> ZkScreen(
            title = null,
            bottom = {
                ZButton(onDone, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large) { ZButtonText("Done") }
            },
        ) {
            VSpace(40)
            SectionTitle("Sent", "${formatZka(amount ?: 0)} ZKA on its way", "It counts once a block includes it.")
            Panel {
                KeyValueRow("Fee", formatZka(result?.optLong("fee_atoms") ?: 0) + " ZKA")
                KeyValueRow("Proof took", "%.1f s".format((result?.optLong("proving_ms") ?: 0) / 1000.0))
                KeyValueRow("Valid until block", "%,d".format(result?.optInt("expiry") ?: 0))
                CopyableKeyValueRow("Transaction id", result?.optString("txid").orEmpty())
            }
        }

        SendStep.Failed -> ZkScreen(
            title = null,
            bottom = {
                ZButton({ step = SendStep.Form }, Modifier.fillMaxWidth(), ZButtonVariant.Outline, ZButtonSize.Large) {
                    ZButtonText("Back")
                }
            },
        ) {
            VSpace(40)
            SectionTitle("Not sent", "The payment did not go out", null)
            Notice(failure.orEmpty(), tone = ZkTone.Err)
        }
    }
}

