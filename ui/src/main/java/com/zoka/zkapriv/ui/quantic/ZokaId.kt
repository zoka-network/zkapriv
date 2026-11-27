package com.zoka.zkapriv.ui.quantic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zoka.zkapriv.ui.avatar.AvatarMosaic
import com.zoka.zkapriv.ui.avatar.ZKAPrivAvatar
import com.zoka.zkapriv.ui.components.ZLabel
import com.zoka.zkapriv.ui.theme.Zk

/**
 * El Zoka ID de una dirección: el mosaico y el código de 80 bits de su forma
 * corta (docs/ZOKA-ID.md del nodo). La misma dirección da el mismo ID en
 * ZKAPriv, ZSilent, la billetera web y ZokaScan: sirve para confirmar a quién
 * se le paga.
 */
@Composable
internal fun ZokaIdRow(short: String?, modifier: Modifier = Modifier, label: String = "Zoka ID") {
    val code = remember(short) { short?.let(AvatarMosaic::codeV2) }
    Row(modifier.testTag("zoka-id"), verticalAlignment = Alignment.CenterVertically) {
        ZKAPrivAvatar(short.orEmpty(), Modifier.size(34.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            ZLabel(label)
            Text(code ?: "—", style = Zk.text.mono, color = Zk.colors.text, maxLines = 1)
        }
    }
}
