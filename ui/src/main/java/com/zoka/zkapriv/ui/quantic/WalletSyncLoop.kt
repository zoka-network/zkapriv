package com.zoka.zkapriv.ui.quantic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Un solo lector, con reintento y pedidos manuales agrupados. */
internal class WalletSyncLoop(
    private val scope: CoroutineScope,
    private val sync: suspend () -> Unit,
    private val intervalMillis: () -> Long,
) {
    private var job: Job? = null
    private val refresh = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                try { sync() } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Un fallo puntual no mata las siguientes actualizaciones.
                }
                withTimeoutOrNull(intervalMillis()) { refresh.receive() }
            }
        }
    }

    fun request() { refresh.trySend(Unit) }
    fun stop() { job?.cancel(); job = null }
}
