package com.zoka.zkapriv.ui.quantic

import com.zoka.zkapriv.network.quantic.QuanticMovement

/** Recent on-chain balance, reconstructed backwards from the current total. Not market returns. */
internal data class BalanceHistory(val balances: List<Long>) {
    val change: Long get() = balances.last() - balances.first()
    val percent: Double? get() = balances.first().takeIf { it > 0 }?.let { change.toDouble() / it * 100 }
}

/**
 * La serie de la curva del inicio: desde el saldo de hoy hacia atrás, con los
 * últimos 40 movimientos confirmados. Es solo para dibujar la tendencia, así
 * que no se cae por un dato raro: lo sin confirmar o de un tipo desconocido se
 * salta, y si el historial está cortado y el saldo daría negativo, queda en 0.
 * Sin movimientos devuelve solo el saldo de hoy (la línea va plana).
 */
internal fun balanceHistory(current: Long, movements: List<QuanticMovement>): BalanceHistory? {
    if (current < 0) return null
    val ordered = movements.asSequence()
        .filter { it.confirmations > 0 && it.amountAtoms >= 0 && it.feeAtoms >= 0 }
        .filter { it.kind == "sent" || it.kind == "received" || it.kind == "mined" }
        .distinctBy { it.txid }
        .sortedWith(compareBy<QuanticMovement> { it.height }.thenBy { it.timestamp }.thenBy { it.txid })
        .toList()
        .takeLast(40)
    return try {
        var balance = current
        val reversed = mutableListOf(current)
        for (movement in ordered.asReversed()) {
            val delta = if (movement.kind == "sent") -Math.addExact(movement.amountAtoms, movement.feeAtoms)
            else movement.amountAtoms
            balance = Math.subtractExact(balance, delta).coerceAtLeast(0)
            reversed.add(balance)
        }
        BalanceHistory(reversed.asReversed())
    } catch (_: ArithmeticException) { BalanceHistory(listOf(current)) }
}
