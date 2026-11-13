package com.zoka.zkapriv.ui.quantic

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.network.quantic.WalletAddress
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.CopyButton
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.MonoText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.Pill
import com.zoka.zkapriv.ui.components.Segmented
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZChip
import com.zoka.zkapriv.ui.components.ZLabel
import com.zoka.zkapriv.ui.components.ZSheet
import com.zoka.zkapriv.ui.components.ZkAnimatedSwap
import com.zoka.zkapriv.ui.components.ZkQrCode
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.components.middleTruncated
import com.zoka.zkapriv.ui.qrscanner.MultiPartQr
import com.zoka.zkapriv.ui.theme.Zk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Recibir: una sola tarjeta con el QR de la dirección elegida y, arriba, una
 * fila de pastillas para pasar de la principal a las otras (hasta siete más).
 * Cada una tiene su forma corta (57 caracteres, un QR común) y la completa
 * (unos 1760) en un QR de varias partes que ZSilent y ZKAPriv leen. La corta
 * sirve cuando la red conoce la completa: la app la publica sola.
 */
@Composable
fun ReceiveScreen(vm: WalletViewModel, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val walletId by vm.activeWallet.collectAsState()
    val state by vm.state.collectAsState()
    val loaded by vm.addresses.collectAsState()
    // Ni la elección ni un alta en curso sobreviven al cambio de billetera.
    key(walletId) {
        // La principal se sabe sin leer el estado: se muestra enseguida.
        val main = remember { WalletAddress(0, vm.shortAddress(), vm.fullAddress(), published = false) }
        val addresses = loaded.ifEmpty { listOf(main.copy(published = state.addressPublished)) }
        var selected by rememberSaveable { mutableIntStateOf(0) }
        var adding by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        ReceiveContent(
            addresses = addresses,
            selected = selected,
            adding = adding,
            error = error,
            onSelect = { selected = it },
            onAdd = {
                adding = true
                error = null
                scope.launch {
                    vm.addAddress()
                        .onSuccess { selected = it.index }
                        .onFailure { error = "Could not add an address. Try again." }
                    adding = false
                }
            },
            onShare = { text ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(send, "Share address"))
            },
            onBack = onBack,
        )
    }
}

/** La pantalla sin estado de la billetera: también la dibuja la vista de diseño. */
@Composable
internal fun ReceiveContent(
    addresses: List<WalletAddress>,
    selected: Int,
    adding: Boolean,
    error: String?,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onShare: (String) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val current = addresses.firstOrNull { it.index == selected } ?: addresses.first()
    var which by rememberSaveable { mutableStateOf("Short") }
    var askNew by remember { mutableStateOf(false) }
    val short = which == "Short"
    val shown = if (short) current.short else current.full
    val extras = addresses.count { it.index > 0 }

    ZkScreen(title = "Receive", onBack = onBack) {
        AddressChips(
            addresses = addresses,
            selected = current.index,
            canAdd = extras < QuanticVault.MAX_EXTRA_ADDRESSES,
            adding = adding,
            onSelect = onSelect,
            onNew = { askNew = true },
        )
        Panel {
            // El QR que sale se dibuja con su propio valor, no con el nuevo.
            ZkAnimatedSwap(short to shown, Modifier.align(Alignment.CenterHorizontally)) { (isShort, value) ->
                if (isShort) ZkQrCode(value) else AnimatedMultiPartQr(value)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ZLabel(if (current.index == 0) "Main address" else "Address #${current.index}")
                MonoText(middleTruncated(shown, head = 16, tail = 12))
            }
            AddressStatus(current.published, short)
            Segmented(listOf("Short", "Full"), which, { which = it }, Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CopyButton(shown, Modifier.weight(1f), variant = ZButtonVariant.Primary, size = ZButtonSize.Large)
            ZButton({ onShare(shown) }, Modifier.weight(1f), ZButtonVariant.Outline, ZButtonSize.Large) {
                Icon(Icons.Outlined.Share, null, tint = LocalContentColor.current, modifier = Modifier.size(16.dp))
                ZButtonText("Share")
            }
        }
        error?.let { Notice(it, tone = ZkTone.Err) }
    }

    if (askNew) {
        NewAddressSheet(
            extras = extras,
            onCreate = {
                askNew = false
                onAdd()
            },
            onDismiss = { askNew = false },
        )
    }
}

/** `Main · #1 · #2 … · New`: la fila se desliza si no entra. */
@Composable
private fun AddressChips(
    addresses: List<WalletAddress>,
    selected: Int,
    canAdd: Boolean,
    adding: Boolean,
    onSelect: (Int) -> Unit,
    onNew: () -> Unit,
) {
    val scroll = rememberScrollState()
    // La recién creada queda elegida y al final de la fila: se la trae a la vista.
    LaunchedEffect(addresses.size) { scroll.scrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        addresses.forEach { address ->
            ZChip(
                text = if (address.index == 0) "Main" else "#${address.index}",
                onClick = { onSelect(address.index) },
                selected = address.index == selected,
            )
        }
        if (canAdd) {
            ZChip(
                text = if (adding) "Adding…" else "New",
                onClick = onNew,
                enabled = !adding,
                role = Role.Button,
                leading = {
                    Icon(Icons.Outlined.Add, null, tint = LocalContentColor.current, modifier = Modifier.size(14.dp))
                },
            )
        }
    }
}

/** Si la red ya conoce la corta. La completa sirve siempre. */
@Composable
private fun AddressStatus(published: Boolean, short: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusDot(on = short && !published)
            Text(
                when {
                    !short -> "Works without the directory"
                    published -> "Ready to receive"
                    else -> "Activating…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Zk.colors.text,
            )
        }
        when {
            !short -> HintText("Scan it with ZKAPriv or ZSilent: the parts can come in any order.")
            !published -> HintText("The network is learning this short address. The full one already works.")
        }
    }
}

/** Crear una dirección no se deshace: se explica y se confirma en una hoja. */
@Composable
private fun NewAddressSheet(extras: Int, onCreate: () -> Unit, onDismiss: () -> Unit) {
    ZSheet(onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "New address",
                style = MaterialTheme.typography.titleLarge,
                color = Zk.colors.text,
                modifier = Modifier.weight(1f),
            )
            Pill("$extras of ${QuanticVault.MAX_EXTRA_ADDRESSES}")
        }
        Text(
            "A different address for each person who pays you: they cannot tell it is the same wallet. " +
                "Everything arrives in this balance.",
            style = MaterialTheme.typography.bodyMedium,
            color = Zk.colors.textMuted,
        )
        HintText("Create it before giving it out: payments to it are found from the next block.")
        ZButton(onCreate, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large) {
            ZButtonText("Create address")
        }
        ZButton(onDismiss, Modifier.fillMaxWidth(), ZButtonVariant.Ghost) { ZButtonText("Cancel") }
    }
}

/** Las partes del QR en ciclo; sin animaciones, se pasan a mano. */
@Composable
private fun AnimatedMultiPartQr(text: String) {
    val parts = remember(text) { MultiPartQr.split(text) }
    var index by remember(text) { mutableIntStateOf(0) }
    val animate = Zk.animations
    LaunchedEffect(parts, animate) {
        while (animate && parts.size > 1) {
            delay(500)
            index = (index + 1) % parts.size
        }
    }
    Column(
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
