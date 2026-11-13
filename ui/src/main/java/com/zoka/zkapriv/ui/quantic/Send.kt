package com.zoka.zkapriv.ui.quantic

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.zoka.zkapriv.network.quantic.QuanticWallet.Companion.formatZka
import com.zoka.zkapriv.network.quantic.QuanticWallet.Companion.parseZka
import com.zoka.zkapriv.ui.components.CopyableKeyValueRow
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.Eyebrow
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.KeyValueRow
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.SectionTitle
import com.zoka.zkapriv.ui.components.StepBars
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZChip
import com.zoka.zkapriv.ui.components.ZInput
import com.zoka.zkapriv.ui.components.ZLabel
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.components.middleTruncated
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay
import org.json.JSONObject

private enum class SendStep { Form, Review, Pin }

/** El memo viaja cifrado con el pago y tiene este tope. */
internal const val MAX_MEMO_BYTES = 512

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

/** Lo que muestra el formulario de envío: lo arma [SendScreen] y la vista de diseño. */
internal data class SendFormState(
    val to: String = "",
    /** `short`, `full` o null si no es una dirección de esta red. */
    val kind: String? = null,
    val amountText: String = "",
    /** El monto en átomos, o null si lo escrito no es un monto. */
    val amountAtoms: Long? = null,
    /** Ya hay cotización para ese monto. */
    val quoted: Boolean = false,
    val enough: Boolean = false,
    val feeAtoms: Long = 0,
    val memo: String = "",
    val spendableAtoms: Long = 0,
    val maxSendableAtoms: Long = 0,
    /** Hay un pago anterior esperando bloque: no se manda otro. */
    val pending: Boolean = false,
) {
    val memoBytes: Int get() = memo.toByteArray(Charsets.UTF_8).size
    val canReview: Boolean
        get() = kind != null && (amountAtoms ?: 0) > 0 && enough && memoBytes <= MAX_MEMO_BYTES && !pending
}

@Composable
fun SendScreen(
    vm: WalletViewModel,
    scanned: String?,
    onScan: () -> Unit,
    onDone: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val state by vm.state.collectAsState()
    val sending by vm.sendState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var step by rememberSaveable { mutableStateOf(SendStep.Form) }
    var to by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    var quote by remember { mutableStateOf<JSONObject?>(null) }
    val view = LocalView.current
    DisposableEffect(sending.running) {
        val previous = view.keepScreenOn
        if (sending.running) view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    val idle = !sending.running && sending.result == null && sending.error == null
    BackHandler(idle && step != SendStep.Form) {
        step = if (step == SendStep.Pin) SendStep.Review else SendStep.Form
    }

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
    LaunchedEffect(amount, state.height, state.spendableAtoms, state.reservedAtoms) {
        quote = null
        if (amount != null && amount > 0) {
            delay(250)
            quote = runCatching { vm.quote(amount) }.getOrNull()
        }
    }
    val form = SendFormState(
        to = to,
        kind = kind,
        amountText = amountText,
        amountAtoms = amount,
        quoted = quote != null,
        enough = quote?.optBoolean("enough") == true,
        feeAtoms = quote?.optLong("fee_atoms") ?: 0L,
        memo = memo,
        spendableAtoms = state.spendableAtoms,
        maxSendableAtoms = state.maxSendableAtoms,
        pending = state.pendingTxid != null,
    )

    fun launchSend() {
        if (amount == null || !form.enough || form.pending) return
        vm.startSend(to.trim(), amount, memo)
        step = SendStep.Form
    }

    fun authorize() {
        val activity = context as? FragmentActivity
        if (vm.biometricEnabled() && activity != null) {
            authenticate(activity, "Send ${formatZka(amount ?: 0)} ZKA", "Confirm it is you", onSuccess = ::launchSend)
        } else {
            step = SendStep.Pin
        }
    }

    val result = sending.result
    val failure = sending.error
    when {
        sending.running -> SendWorkingContent(sending.phase, sending.startedAt)
        result != null -> SendDoneContent(
            amountAtoms = sending.amountAtoms,
            feeAtoms = result.optLong("fee_atoms"),
            provingMs = result.optLong("proving_ms"),
            expiry = result.optInt("expiry"),
            txid = result.optString("txid"),
            onDone = {
                vm.clearSendResult()
                onDone()
            },
        )
        failure != null -> SendFailedContent(failure, pending = state.pendingTxid != null) {
            vm.clearSendResult()
            step = SendStep.Form
        }
        step == SendStep.Review -> SendReviewContent(form, vm.torMode(), onBack = { step = SendStep.Form }, onSend = ::authorize)
        step == SendStep.Pin -> ZkScreen(title = "Confirm", onBack = { step = SendStep.Review }, scroll = false) {
            PinPrompt(
                title = "Confirm with your PIN",
                check = vm::checkPin,
                waitMillis = vm::pinWaitMillis,
                onOk = ::launchSend,
                subtitle = "Send ${displayZka(amount ?: 0)} ZKA",
            )
        }
        else -> SendFormContent(
            form,
            onTo = { to = it.trim() },
            onAmount = { amountText = it.replace(',', '.') },
            onMemo = { memo = it },
            onPaste = { clipboard.getText()?.text?.let { to = parsePaymentText(it).first } },
            onScan = onScan,
            onMax = { amountText = formatZka(state.maxSendableAtoms).replace(",", "") },
            onReview = { step = SendStep.Review },
            onBack = onBack,
        )
    }
}

/** El formulario: a quién, cuánto y el memo. */
@Composable
internal fun SendFormContent(
    form: SendFormState,
    onTo: (String) -> Unit,
    onAmount: (String) -> Unit,
    onMemo: (String) -> Unit,
    onPaste: () -> Unit,
    onScan: () -> Unit,
    onMax: () -> Unit,
    onReview: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val c = Zk.colors
    val badAddress = form.to.isNotBlank() && form.kind == null
    val badAmount = form.amountText.isNotBlank() && (form.amountAtoms == null || (form.quoted && !form.enough))
    ZkScreen(
        title = "Send",
        onBack = onBack,
        actions = {
            Column(horizontalAlignment = Alignment.End) {
                ZLabel("Spendable")
                Text(displayZka(form.spendableAtoms), style = Zk.text.mono, color = c.text, maxLines = 1)
            }
        },
        bottom = {
            ZButton(onReview, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large, enabled = form.canReview) {
                ZButtonText("Review")
                ForwardMark()
            }
        },
    ) {
        if (form.pending) {
            Notice(
                "Wait until your last payment is mined (or expires) to send another.",
                title = "A payment is pending",
                tone = ZkTone.Warn,
            )
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZLabel("To")
            ZInput(
                value = form.to,
                onValueChange = onTo,
                modifier = Modifier.fillMaxWidth(),
                placeholder = "zqtest2… short or full address",
                isError = badAddress,
                trailing = {
                    Row {
                        FieldIcon(Icons.Outlined.ContentPaste, "Paste", c.textMuted, onPaste)
                        FieldIcon(Icons.Outlined.QrCodeScanner, "Scan", c.accent, onScan)
                    }
                },
            )
            when {
                badAddress -> ErrorText("Not a Zoka Quantic testnet address.")
                form.kind == "short" -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Pill("Short", tone = ZkTone.Accent)
                    HintText("Resolved by the network when you send.")
                }
                form.kind == "full" -> Pill("Full address", tone = ZkTone.Accent)
            }
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ZLabel("Amount", Modifier.weight(1f))
                ZChip("MAX", onMax, enabled = form.maxSendableAtoms > 0, role = Role.Button)
            }
            ZInput(
                value = form.amountText,
                onValueChange = onAmount,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                placeholder = "0.00",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = badAmount,
                textStyle = Zk.text.monoLarge,
                trailing = { Text("ZKA", style = Zk.text.mono, color = c.accent, modifier = Modifier.padding(end = 10.dp)) },
            )
            when {
                form.amountText.isNotBlank() && form.amountAtoms == null -> ErrorText("Up to 6 decimals.")
                form.quoted && !form.enough -> ErrorText("That amount exceeds what you can send from your available balance.")
                form.quoted -> HintText("Fee ${formatZka(form.feeAtoms)} ZKA")
            }
        }
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                ZLabel("Memo", Modifier.weight(1f))
                Text(
                    "${form.memoBytes} / $MAX_MEMO_BYTES",
                    style = Zk.text.mono.copy(fontSize = 11.sp),
                    color = if (form.memoBytes > MAX_MEMO_BYTES) c.danger else c.textMuted,
                )
            }
            ZInput(
                value = form.memo,
                onValueChange = onMemo,
                modifier = Modifier.fillMaxWidth().heightIn(min = 76.dp),
                placeholder = "Optional. Encrypted with your payment.",
                mono = false,
                singleLine = false,
                isError = form.memoBytes > MAX_MEMO_BYTES,
            )
        }
    }
}

/** Un ícono que se toca dentro de un campo. */
@Composable
private fun FieldIcon(icon: ImageVector, description: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** La flecha de los botones que llevan al paso siguiente. */
@Composable
internal fun ForwardMark() {
    Icon(Icons.Outlined.NorthEast, null, tint = LocalContentColor.current, modifier = Modifier.size(15.dp))
}

/** El resumen antes de autorizar: monto, destino, comisión y total. */
@Composable
internal fun SendReviewContent(form: SendFormState, tor: Boolean, onBack: () -> Unit, onSend: () -> Unit) {
    val amount = form.amountAtoms ?: 0
    ZkScreen(
        title = "Review",
        onBack = onBack,
        bottom = {
            ZButton(onSend, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large, enabled = form.enough && !form.pending) {
                ZButtonText("Send")
                ForwardMark()
            }
        },
    ) {
        Panel(contentPadding = PaddingValues(20.dp), hero = true) {
            Eyebrow("You send", dot = true)
            FittedAmount(displayZka(amount), max = 40.sp, alignment = Alignment.CenterStart)
            ThinDivider()
            Column {
                KeyValueRow("To", middleTruncated(form.to.trim(), 14, 10))
                KeyValueRow("Network fee", formatZka(form.feeAtoms) + " ZKA")
                KeyValueRow("Total", formatZka(amount + form.feeAtoms) + " ZKA")
                if (form.memo.isNotBlank()) KeyValueRow("Memo", form.memo)
            }
        }
        Notice(
            "This phone builds the zero-knowledge proof itself, and that takes a while. Keep ZKAPriv open " +
                "until it is sent." + if (tor) " Tor mode is on: sending goes through Tor." else "",
            title = "Proof on this phone",
            tone = ZkTone.Info,
        )
    }
}

/** Mientras el teléfono prueba y manda el pago: las fases reales del motor. */
@Composable
internal fun SendWorkingContent(phase: Int, startedAt: Long) {
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            elapsed = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    ZkScreen(title = "Private payment") {
        ProofMatrix(ProofResult.Working)
        SectionTitle(
            "Zoka Quantic · STARK",
            when (phase) {
                1 -> "Connecting to the network…"
                2 -> "Generating the proof…"
                3 -> "Broadcasting payment…"
                else -> "Preparing your payment…"
            },
            "The zero-knowledge proof is generated on your phone. Your payment uses Quantic’s hybrid post-quantum encryption.",
        )
        // Cuatro fases que informa el motor; ningún porcentaje inventado.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StepBars(phase.coerceIn(0, 3) + 1, 4)
            Text("%d:%02d".format(elapsed / 60, elapsed % 60), style = Zk.text.mono, color = Zk.colors.textMuted)
        }
        HintText("Keep ZKAPriv open while your payment completes.")
    }
}

@Composable
internal fun SendDoneContent(
    amountAtoms: Long,
    feeAtoms: Long,
    provingMs: Long,
    expiry: Int,
    txid: String,
    onDone: () -> Unit,
) {
    ZkScreen(
        title = null,
        bottom = {
            ZButton(onDone, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large) { ZButtonText("Done") }
        },
    ) {
        ProofMatrix(ProofResult.Accepted)
        SectionTitle(
            "Payment accepted",
            "${displayZka(amountAtoms)} ZKA on its way",
            "The network accepted your payment. It is waiting to be included in a block.",
        )
        Panel(gap = 0.dp) {
            KeyValueRow("Fee", formatZka(feeAtoms) + " ZKA")
            KeyValueRow("Proof took", "%.1f s".format(provingMs / 1000.0))
            KeyValueRow("Valid until block", "%,d".format(expiry))
            CopyableKeyValueRow("Transaction id", txid)
        }
    }
}

@Composable
internal fun SendFailedContent(message: String, pending: Boolean, onBack: () -> Unit) {
    ZkScreen(
        title = null,
        bottom = {
            ZButton(onBack, Modifier.fillMaxWidth(), ZButtonVariant.Secondary, ZButtonSize.Large) { ZButtonText("Back") }
        },
    ) {
        ProofMatrix(ProofResult.Failed)
        SectionTitle("Check your payment", if (pending) "Awaiting confirmation" else "Could not send", null)
        Notice(message, tone = ZkTone.Err)
        if (pending) {
            HintText("The payment may have reached the network. Funds stay reserved until confirmation or expiry. Do not repeat it yet.")
        }
    }
}
