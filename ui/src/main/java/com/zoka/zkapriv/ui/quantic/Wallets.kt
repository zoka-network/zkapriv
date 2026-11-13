package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.security.vault.QuanticVault
import com.zoka.zkapriv.ui.components.CheckBadge
import com.zoka.zkapriv.ui.components.GlassIconButton
import com.zoka.zkapriv.ui.components.IconSquare
import com.zoka.zkapriv.ui.components.Panel
import com.zoka.zkapriv.ui.components.ZSheet
import com.zoka.zkapriv.ui.components.ErrorText
import com.zoka.zkapriv.ui.components.HintText
import com.zoka.zkapriv.ui.components.Notice
import com.zoka.zkapriv.ui.components.ThinDivider
import com.zoka.zkapriv.ui.components.ZButton
import com.zoka.zkapriv.ui.components.ZButtonSize
import com.zoka.zkapriv.ui.components.ZButtonText
import com.zoka.zkapriv.ui.components.ZButtonVariant
import com.zoka.zkapriv.ui.components.ZInput
import com.zoka.zkapriv.ui.components.ZLabel
import com.zoka.zkapriv.ui.components.ZkTone
import com.zoka.zkapriv.ui.theme.Zk

/**
 * Las billeteras de este teléfono. Tocar una pasa a ella; el lápiz la
 * renombra o la saca. Un solo PIN para todas.
 */
@Composable
fun WalletsScreen(vm: WalletViewModel, onBack: () -> Unit, onCreate: () -> Unit, onRestore: () -> Unit) {
    val wallets by vm.wallets.collectAsState()
    val active by vm.activeWallet.collectAsState()
    val sending by vm.sendState.collectAsState()
    var editing by remember { mutableStateOf<QuanticVault.WalletEntry?>(null) }
    var switching by remember { mutableStateOf(false) }
    WalletsContent(
        wallets,
        active,
        busy = sending.running || switching,
        sending = sending.running,
        onBack = onBack,
        onSelect = { id ->
            switching = true
            vm.switchWallet(id) {
                switching = false
                onBack()
            }
        },
        onEdit = { editing = it },
        onCreate = onCreate,
        onRestore = onRestore,
    )
    editing?.let { entry ->
        EditWalletDialog(
            entry,
            canRemove = wallets.size > 1 && !sending.running,
            onRename = { name -> vm.renameWallet(entry.id, name) },
            onRemove = { done -> vm.removeWallet(entry.id) { result -> done(result) } },
            onDismiss = { editing = null },
        )
    }
}

/** La lista y sus botones, sin estado: también la dibuja la vista de diseño. */
@Composable
internal fun WalletsContent(
    wallets: List<QuanticVault.WalletEntry>,
    active: String,
    busy: Boolean,
    sending: Boolean,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onEdit: (QuanticVault.WalletEntry) -> Unit,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
) {
    ZkScreen(
        title = "Wallets",
        onBack = onBack,
        bottom = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZButton(onCreate, Modifier.fillMaxWidth(), ZButtonVariant.Primary, ZButtonSize.Large, enabled = !sending) {
                    ZButtonText("Create new wallet")
                }
                ZButton(onRestore, Modifier.fillMaxWidth(), ZButtonVariant.Secondary, ZButtonSize.Large, enabled = !sending) {
                    ZButtonText("Restore with 24 words")
                }
            }
        },
    ) {
        if (sending) {
            Notice("Wait until your payment finishes to change wallets.", tone = ZkTone.Warn)
        }
        Panel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), gap = 0.dp) {
            wallets.forEachIndexed { i, entry ->
                if (i > 0) ThinDivider()
                WalletRow(
                    entry,
                    active = entry.id == active,
                    enabled = !busy,
                    onSelect = { onSelect(entry.id) },
                    onEdit = { onEdit(entry) },
                )
            }
        }
        HintText("All wallets use the same PIN. Each one has its own 24 words: write them down.")
    }
}

@Composable
private fun WalletRow(
    entry: QuanticVault.WalletEntry,
    active: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val c = Zk.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !active, onClick = onSelect)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconSquare(Icons.Outlined.AccountBalanceWallet, active = active, size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(entry.name, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (active) "In use" else "Tap to use", style = MaterialTheme.typography.bodySmall, color = if (active) c.accent else c.textMuted)
        }
        if (active) CheckBadge()
        GlassIconButton(Icons.Outlined.Edit, "Rename or remove ${entry.name}", onEdit, enabled = enabled)

    }
}

@Composable
internal fun EditWalletDialog(
    entry: QuanticVault.WalletEntry,
    canRemove: Boolean,
    onRename: (String) -> Result<Unit>,
    onRemove: ((Result<Unit>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(entry.name) }
    var confirmRemove by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    ZSheet(onDismiss = { if (!busy) onDismiss() }) {
        Text(if (confirmRemove) "Remove ${entry.name}?" else "Edit wallet", style = MaterialTheme.typography.titleLarge, color = Zk.colors.text)
        if (confirmRemove) {
            Notice("It leaves this phone. Only its 24 words bring it back, with its funds.", tone = ZkTone.Err)
        } else {
            ZLabel("Name")
            ZInput(name, { name = it.take(QuanticVault.MAX_NAME) }, Modifier.fillMaxWidth(), mono = false)
        }
        error?.let { ErrorText(it) }
        ZButton(
            {
                if (confirmRemove) {
                    busy = true
                    onRemove { result ->
                        busy = false
                        result.onSuccess { onDismiss() }.onFailure { error = it.message ?: "Could not remove it" }
                    }
                } else {
                    onRename(name).onSuccess { onDismiss() }.onFailure { error = it.message ?: "Could not rename it" }
                }
            },
            Modifier.fillMaxWidth(),
            if (confirmRemove) ZButtonVariant.Danger else ZButtonVariant.Primary,
            enabled = !busy && (confirmRemove || name.isNotBlank()),
        ) { ZButtonText(if (busy) "Removing…" else if (confirmRemove) "Remove" else "Save") }
        if (canRemove && !confirmRemove) {
            ZButton({ confirmRemove = true }, Modifier.fillMaxWidth(), ZButtonVariant.Danger) {
                ZButtonText("Remove from this phone")
            }
        }
        ZButton({ if (confirmRemove) confirmRemove = false else onDismiss() }, Modifier.fillMaxWidth(), ZButtonVariant.Ghost, enabled = !busy) {
            ZButtonText("Cancel")
        }
    }
}

/** El último paso de una billetera más: su nombre. Después pasa a ella. */
@Composable
fun WalletNameScreen(vm: WalletViewModel, onBack: () -> Unit, onAdded: () -> Unit) {
    var name by remember { mutableStateOf(vm.nextWalletName()) }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    ZkScreen(
        title = "Name your wallet",
        onBack = onBack,
        bottom = {
            ZButton(
                {
                    adding = true
                    vm.addDraftWallet(name) { result ->
                        adding = false
                        result.onSuccess { onAdded() }.onFailure { error = it.message ?: "Could not add the wallet" }
                    }
                },
                Modifier.fillMaxWidth(),
                ZButtonVariant.Primary,
                ZButtonSize.Large,
                enabled = !adding && name.isNotBlank(),
            ) { ZButtonText(if (adding) "Adding…" else "Add wallet") }
        },
    ) {
        ZLabel("Name")
        ZInput(name, { name = it.take(QuanticVault.MAX_NAME) }, Modifier.fillMaxWidth(), mono = false)
        HintText("Only you see this name. It reads its balance from the chain after it is added.")
        error?.let { ErrorText(it) }
    }
}
