package com.looper.base.equalizer.model

object EqConstants {
    val STANDARD_FREQS_HZ: List<Int> = listOf(60, 230, 910, 3_600, 14_000)
    const val BAND_COUNT = 5

    const val MIN_LEVEL_MB = -1500
    const val MAX_LEVEL_MB = 1500

    const val MIN_STRENGTH = 0
    const val MAX_STRENGTH = 1000

    const val MIN_LOUDNESS_MB = 0
    const val MAX_LOUDNESS_MB = 1000

    const val PRESET_ID_FLAT = "flat"

    const val AUDIO_SESSION_UNSET = 0
}

data class EqSettings(
    val enabled: Boolean = false,
    val presetId: String? = EqConstants.PRESET_ID_FLAT,
    val bandLevelsMb: List<Int> = List(EqConstants.BAND_COUNT) { 0 },
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudnessGainMb: Int = 0,
) {
    val isCustom: Boolean get() = presetId == null

    fun normalized(): EqSettings {
        val levels = List(EqConstants.BAND_COUNT) { index ->
            (bandLevelsMb.getOrNull(index) ?: 0).coerceIn(
                minimumValue = EqConstants.MIN_LEVEL_MB,
                maximumValue = EqConstants.MAX_LEVEL_MB
            )
        }
        return copy(
            bandLevelsMb = levels,
            bassBoost = bassBoost.coerceIn(
                minimumValue = EqConstants.MIN_STRENGTH,
                maximumValue = EqConstants.MAX_STRENGTH
            ),
            virtualizer = virtualizer.coerceIn(
                minimumValue = EqConstants.MIN_STRENGTH,
                maximumValue = EqConstants.MAX_STRENGTH
            ),
            loudnessGainMb = loudnessGainMb.coerceIn(
                minimumValue = EqConstants.MIN_LOUDNESS_MB,
                maximumValue = EqConstants.MAX_LOUDNESS_MB
            ),
        )
    }
}

data class EqPreset(
    val id: String,
    val name: String,
    val bandLevelsMb: List<Int>,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val isBuiltIn: Boolean,
)

fun EqPreset.applyTo(settings: EqSettings): EqSettings = settings.copy(
    presetId = id,
    bandLevelsMb = bandLevelsMb,
    bassBoost = bassBoost,
    virtualizer = virtualizer,
).normalized()

data class EqCapabilities(
    val supported: Boolean,
    val attached: Boolean,
    val bandCount: Int,
    val centerFreqHz: List<Int>,
    val minLevelMb: Int,
    val maxLevelMb: Int,
) {
    companion object {
        fun unattached(supported: Boolean) = EqCapabilities(
            supported = supported,
            attached = false,
            bandCount = EqConstants.BAND_COUNT,
            centerFreqHz = EqConstants.STANDARD_FREQS_HZ,
            minLevelMb = EqConstants.MIN_LEVEL_MB,
            maxLevelMb = EqConstants.MAX_LEVEL_MB,
        )
    }
}

object BuiltInEqPresets {

    private fun preset(
        id: String,
        name: String,
        vararg levels: Int,
        bass: Int = 0,
        virtualizer: Int = 0
    ) =
        EqPreset(
            id = id,
            name = name,
            bandLevelsMb = levels.toList(),
            bassBoost = bass,
            virtualizer = virtualizer,
            isBuiltIn = true,
        )

    val all: List<EqPreset> = listOf(
        preset(EqConstants.PRESET_ID_FLAT, "Flat", 0, 0, 0, 0, 0),
        preset("normal", "Normal", 300, 0, 0, 0, 300),
        preset("pop", "Pop", -100, 200, 500, 100, -200),
        preset("rock", "Rock", 500, 300, -100, 300, 500),
        preset("jazz", "Jazz", 400, 200, -200, 200, 500),
        preset("classical", "Classical", 500, 300, -200, 400, 400),
        preset("dance", "Dance", 600, 0, 200, 400, 100),
        preset("hiphop", "Hip Hop", 500, 300, 0, 100, 300),
        preset("heavy_metal", "Heavy Metal", 400, 100, 900, 300, 0),
        preset("folk", "Folk", 300, 0, 0, 200, -100),
        preset("electronic", "Electronic", 500, 300, 0, 200, 500),
        preset("acoustic", "Acoustic", 400, 300, 100, 300, 300),
        preset("vocal", "Vocal", -200, 0, 400, 500, 0),
        preset("bass_boost", "Bass Boost", 800, 500, 0, 0, 0, bass = 600),
        preset("treble_boost", "Treble Boost", 0, 0, 0, 500, 800),
    )

    val flat: EqPreset get() = all.first()

    fun find(id: String?): EqPreset? = id?.let { key ->
        all.firstOrNull {
            it.id == key
        }
    }
}
