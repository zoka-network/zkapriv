package com.zoka.zkapriv.network.quantic

import org.json.JSONObject

/** Optional display data, never used for consensus or balance calculations. */
data class NetworkDisplayStats(val hashrateHps: Long, val difficultyBits: Int?) {
    companion object {
        fun from(json: JSONObject) = NetworkDisplayStats(
            json.optLong("hashrate_estimate_hps", 0).coerceAtLeast(0),
            json.optInt("next_difficulty_bits", -1).takeIf { it in 0..256 },
        )
    }
}
