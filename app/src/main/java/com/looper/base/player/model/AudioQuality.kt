package com.looper.base.player.model

enum class AudioQuality(val maxBitrateKbps: Double?) {
    AUTO(maxBitrateKbps = null),
    LOW(maxBitrateKbps = 64.0),
    NORMAL(maxBitrateKbps = 128.0),
    HIGH(maxBitrateKbps = 320.0),
    LOSSLESS(maxBitrateKbps = null),
}
