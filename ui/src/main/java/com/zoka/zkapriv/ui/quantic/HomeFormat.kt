package com.zoka.zkapriv.ui.quantic

import com.zoka.zkapriv.network.quantic.QuanticWallet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** "12,450.00", "5,000,000.0092": entre 2 y 6 decimales, sin ceros de más. */
internal fun displayZka(atoms: Long): String {
    val per = QuanticWallet.ATOMS_PER_ZKA
    val whole = atoms / per
    val fraction = (atoms % per).toString().padStart(6, '0').trimEnd('0').padEnd(2, '0')
    return String.format(Locale.US, "%,d.%s", whole, fraction)
}

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** "Today · 14:32", "Yesterday · 18:06" o "Jan 24 · 09:17". */
internal fun activityTime(seconds: Long, zone: ZoneId = ZoneId.systemDefault(), today: LocalDate = LocalDate.now(zone)): String {
    if (seconds <= 0) return "Waiting for a block"
    val at = Instant.ofEpochSecond(seconds).atZone(zone)
    val day = when (at.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DAY.format(at)
    }
    return "$day · ${TIME.format(at)}"
}

/** "841 H/s", "1.3 kH/s", "2.4 MH/s". */
internal fun displayHashrate(hps: Long): String = when {
    hps <= 0 -> "—"
    hps < 1_000 -> "$hps H/s"
    hps < 1_000_000 -> String.format(Locale.US, "%.1f kH/s", hps / 1_000.0)
    else -> String.format(Locale.US, "%.1f MH/s", hps / 1_000_000.0)
}
