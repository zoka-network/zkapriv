package com.zoka.zkapriv.ui.quantic

import com.zoka.zkapriv.network.quantic.QuanticMovement

/** Recent on-chain balance, reconstructed backwards from the current total. Not market returns. */
internal data class BalanceHistory(val balances: List<Long>) {
    val change: Long get() = balances.last() - balances.first()
    val percent: Double? get() = balances.first().takeIf { it > 0 }?.let { change.toDouble() / it * 100 }
}

internal fun balanceHistory(current: Long, movements: List<QuanticMovement>): BalanceHistory? {
    if (current < 0 || movements.isEmpty()) return null
    val ordered = movements.distinctBy { it.txid }.sortedWith(
        compareBy<QuanticMovement> { it.height }.thenBy { it.timestamp }.thenBy { it.txid },
    ).takeLast(40)
    return try {
        var balance = current
        val reversed = mutableListOf(current)
        for (movement in ordered.asReversed()) {
            if (movement.amountAtoms < 0 || movement.feeAtoms < 0 || movement.confirmations <= 0) return null
            val delta = when (movement.kind) {
                "sent" -> -Math.addExact(movement.amountAtoms, movement.feeAtoms)
                "received", "mined" -> movement.amountAtoms
                else -> return null
            }
            balance = Math.subtractExact(balance, delta)
            if (balance < 0) return null // Partial/unsynced history is not a fabricated chart.
            reversed.add(balance)
        }
        BalanceHistory(reversed.asReversed())
    } catch (_: ArithmeticException) { null }
}
