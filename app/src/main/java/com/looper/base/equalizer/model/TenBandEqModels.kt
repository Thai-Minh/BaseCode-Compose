package com.looper.base.equalizer.model

enum class EqEngine { AUDIOFX, BUILT_IN }

object TenBandEqConstants {
    val FREQUENCIES_HZ: List<Int> =
        listOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)
    const val BAND_COUNT = 10
    const val Q = 1.41f
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val MIN_PREAMP_DB = -20f
    const val MAX_PREAMP_DB = 6f
}

data class TenBandEqSettings(
    val engine: EqEngine = EqEngine.AUDIOFX,
    val gainsDb: List<Float> = List(size = TenBandEqConstants.BAND_COUNT) {
        0f
    },
    val preampDb: Float = 0f,
    val profileName: String? = null,
) {
    fun normalized(): TenBandEqSettings = copy(
        gainsDb = List(size = TenBandEqConstants.BAND_COUNT) { index ->
            (gainsDb.getOrNull(index) ?: 0f).coerceIn(
                minimumValue = TenBandEqConstants.MIN_GAIN_DB,
                maximumValue = TenBandEqConstants.MAX_GAIN_DB
            )
        },
        preampDb = preampDb.coerceIn(
            minimumValue = TenBandEqConstants.MIN_PREAMP_DB,
            maximumValue = TenBandEqConstants.MAX_PREAMP_DB
        ),
    )
}

fun EqSettings.forAudioFx(tenBand: TenBandEqSettings): EqSettings =
    if (tenBand.engine == EqEngine.BUILT_IN) {
        copy(bandLevelsMb = List(size = EqConstants.BAND_COUNT) {
            0
        })
    } else {
        this
    }
