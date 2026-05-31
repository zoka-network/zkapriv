package com.zoka.zkapriv.network.config

import android.content.Context
import android.content.SharedPreferences
import com.zoka.zkapriv.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RpcSettings @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedEndpoint: String? = null

    fun getEndpoint(): String {
        cachedEndpoint?.let { return it }
        val stored = prefs.getString(KEY_RPC_ENDPOINT, null)?.trim()
        val resolved = when {
            stored.isNullOrBlank() -> Constants.ZOKA_MAINNET_RPC
            isLanEndpoint(stored) -> Constants.ZOKA_MAINNET_RPC
            else -> stored
        }
        cachedEndpoint = resolved
        return resolved
    }

    private fun runLanRpcMigration() {
        if (prefs.getBoolean(KEY_LAN_MIGRATION_DONE, false)) return
        val stored = prefs.getString(KEY_RPC_ENDPOINT, null)?.trim().orEmpty()
        if (stored.isNotBlank() && isLanEndpoint(stored)) {
            prefs.edit()
                .remove(KEY_RPC_ENDPOINT)
                .putBoolean(KEY_LAN_MIGRATION_DONE, true)
                .apply()
        } else {
            prefs.edit().putBoolean(KEY_LAN_MIGRATION_DONE, true).apply()
        }
    }

    // Catches LAN endpoints set manually after v1 migration already ran.
    private fun runLanRpcMigrationV2() {
        if (prefs.getBoolean(KEY_LAN_MIGRATION_V2_DONE, false)) return
        val stored = prefs.getString(KEY_RPC_ENDPOINT, null)?.trim().orEmpty()
        if (stored.isNotBlank() && isLanEndpoint(stored)) {
            prefs.edit()
                .remove(KEY_RPC_ENDPOINT)
                .putBoolean(KEY_LAN_MIGRATION_V2_DONE, true)
                .apply()
        } else {
            prefs.edit().putBoolean(KEY_LAN_MIGRATION_V2_DONE, true).apply()
        }
    }

    private fun isLanEndpoint(url: String): Boolean {
        val host = url
            .substringAfter("://", url)
            .substringBefore('/')
            .substringBefore(':')
            .lowercase()
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host.startsWith("10.") ||
            host.startsWith("192.168.") ||
            (host.startsWith("172.") && run {
                val second = host.split('.').getOrNull(1)?.toIntOrNull()
                second != null && second in 16..31
            })
    }

    fun isCustomEndpoint(): Boolean {
        val stored = prefs.getString(KEY_RPC_ENDPOINT, null)?.trim().orEmpty()
        return stored.isNotBlank() && stored != Constants.ZOKA_MAINNET_RPC
    }

    fun setEndpoint(url: String?) {
        val normalized = url?.trim()?.takeIf { it.isNotBlank() }
        if (normalized == null) {
            prefs.edit().remove(KEY_RPC_ENDPOINT).apply()
        } else {
            val withSlash = if (normalized.endsWith('/')) normalized else "$normalized/"
            prefs.edit().putString(KEY_RPC_ENDPOINT, withSlash).apply()
        }
        cachedEndpoint = null
        clearAllBalanceCaches()
    }

    fun resetToDefault() = setEndpoint(null)

    fun clearAll() {
        prefs.edit().clear().apply()
        cachedEndpoint = null
    }

    fun saveBalanceCache(
        address: String,
        matureAtoms: Long,
        immatureAtoms: Long,
        missingTxCount: Int,
        scannedHeight: Long
    ) {
        prefs.edit()
            .putLong(balanceKey(address, "mature"), matureAtoms)
            .putLong(balanceKey(address, "immature"), immatureAtoms)
            .putInt(balanceKey(address, "missing"), missingTxCount)
            .putLong(balanceKey(address, "height"), scannedHeight)
            .putLong(balanceKey(address, "ts"), System.currentTimeMillis())
            .apply()
    }

    fun readBalanceCache(address: String): CachedBalance? {
        val ts = prefs.getLong(balanceKey(address, "ts"), 0L)
        if (ts == 0L) return null
        return CachedBalance(
            matureAtoms = prefs.getLong(balanceKey(address, "mature"), 0L),
            immatureAtoms = prefs.getLong(balanceKey(address, "immature"), 0L),
            missingTxCount = prefs.getInt(balanceKey(address, "missing"), 0),
            scannedHeight = prefs.getLong(balanceKey(address, "height"), 0L),
            savedAtMillis = ts
        )
    }

    private fun clearAllBalanceCaches() {
        val toRemove = prefs.all.keys.filter { it.startsWith(BALANCE_PREFIX) }
        if (toRemove.isEmpty()) return
        val editor = prefs.edit()
        toRemove.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun balanceKey(address: String, field: String) =
        "$BALANCE_PREFIX${address}__$field"

    data class CachedBalance(
        val matureAtoms: Long,
        val immatureAtoms: Long,
        val missingTxCount: Int,
        val scannedHeight: Long,
        val savedAtMillis: Long
    )

    // ── Pending outbound transfers ──────────────────────────────────────
    //
    // Records every send the user initiated locally so the dashboard can
    // show a "pending" column subtracted from the available balance.
    // Records are pruned by the balance scan when either:
    //   - one of the tx's input nullifiers shows up as spent on chain
    //     (the tx confirmed; the spend is now reflected in `mature`), or
    //   - the entry is older than PENDING_MAX_AGE_MS (assumed dropped).
    //
    // Each record is stored as a single string-valued pref under
    // `pending_send__<address>__<hash>` = `<reservedAtoms>:<ts>` so we
    // can scan + delete by prefix without serializing a JSON list.

    data class PendingSend(
        val hash: String,
        val reservedAtoms: Long,
        val createdAtMillis: Long
    )

    fun recordPendingSend(address: String, hash: String, reservedAtoms: Long) {
        prefs.edit()
            .putString(
                pendingSendKey(address, hash),
                "$reservedAtoms:${System.currentTimeMillis()}"
            )
            .apply()
    }

    fun readPendingSends(address: String): List<PendingSend> {
        val prefix = pendingSendPrefix(address)
        return prefs.all.entries
            .asSequence()
            .filter { it.key.startsWith(prefix) }
            .mapNotNull { entry ->
                val hash = entry.key.removePrefix(prefix)
                val raw = entry.value as? String ?: return@mapNotNull null
                val parts = raw.split(":")
                if (parts.size != 2) return@mapNotNull null
                val atoms = parts[0].toLongOrNull() ?: return@mapNotNull null
                val ts = parts[1].toLongOrNull() ?: return@mapNotNull null
                PendingSend(hash, atoms, ts)
            }
            .toList()
    }

    fun removePendingSend(address: String, hash: String) {
        prefs.edit().remove(pendingSendKey(address, hash)).apply()
    }

    /**
     * Drop entries older than [maxAgeMillis] (assumed to have been
     * dropped from every mempool by now) and return the survivors.
     */
    fun expirePendingSends(address: String, maxAgeMillis: Long = PENDING_MAX_AGE_MS): List<PendingSend> {
        val now = System.currentTimeMillis()
        val remaining = mutableListOf<PendingSend>()
        val editor = prefs.edit()
        for (entry in readPendingSends(address)) {
            if (now - entry.createdAtMillis > maxAgeMillis) {
                editor.remove(pendingSendKey(address, entry.hash))
            } else {
                remaining.add(entry)
            }
        }
        editor.apply()
        return remaining
    }

    private fun pendingSendPrefix(address: String) = "${PENDING_SEND_PREFIX}${address}__"
    private fun pendingSendKey(address: String, hash: String) =
        "${pendingSendPrefix(address)}$hash"

    companion object {
        private const val PREFS_NAME = "zoka_rpc_settings"
        private const val KEY_RPC_ENDPOINT = "rpc_endpoint_url"
        private const val KEY_LAN_MIGRATION_DONE = "rpc_lan_migration_v1_done"
        private const val KEY_LAN_MIGRATION_V2_DONE = "rpc_lan_migration_v2_done"
        private const val BALANCE_PREFIX = "balance_cache__"
        private const val PENDING_SEND_PREFIX = "pending_send__"

        /** 30 minutes — past this, an unconfirmed tx is assumed dropped. */
        const val PENDING_MAX_AGE_MS: Long = 30L * 60L * 1000L
    }
}
