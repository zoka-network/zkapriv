package com.zoka.zkapriv.network.quantic

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** El alcance se comprueba también al volver de JNI: un motor anterior no debe ampliarlo. */
enum class ViewKeyScope(val wireName: String, val title: String, private val prefix: String) {
    INCOMING("incoming", "Incoming view key", "zqtestviewin2"),
    FULL("full", "Full view key", "zqtestview2");

    fun validateExport(kind: String, key: String): String {
        check(kind == wireName && key.startsWith(prefix) && key.length > prefix.length) {
            "The wallet engine could not export the requested view key. Update ZKAPriv and try again."
        }
        return key
    }
}

/** Se llama con el candado de la billetera tomado, incluso después de esperar una sincronización. */
internal suspend fun exportViewKeyForWallet(
    expectedWalletId: String,
    activeWalletId: () -> String,
    scope: ViewKeyScope,
    export: (ViewKeyScope) -> String,
): String {
    currentCoroutineContext().ensureActive()
    check(expectedWalletId == activeWalletId()) { "The active wallet changed. Open its view key again." }
    val key = export(scope)
    currentCoroutineContext().ensureActive()
    check(expectedWalletId == activeWalletId()) { "The active wallet changed. Open its view key again." }
    return key
}
