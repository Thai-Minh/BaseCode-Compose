package com.looper.base.equalizer.model

data class TenBandPreset(
    val id: String,
    val name: String,
    val gainsDb: List<Float>,
    val preampDb: Float = 0f,
)

object TenBandPresets {

    private fun preset(id: String, name: String, vararg gains: Float, preamp: Float = 0f) =
        TenBandPreset(
            id = id,
            name = name,
            gainsDb = gains.toList(),
            preampDb = preamp,
        )

    val all: List<TenBandPreset> = listOf(
        preset("flat", "Flat", 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        preset(
            "acoustic",
            "Acoustic",
            5f,
            4.5f,
            3.5f,
            1f,
            1.5f,
            1.5f,
            3f,
            3.5f,
            3f,
            2f,
            preamp = -4f
        ),
        preset(
            "bass_booster",
            "Bass Booster",
            5.5f,
            4.5f,
            3.5f,
            2.5f,
            1f,
            0f,
            0f,
            0f,
            0f,
            0f,
            preamp = -4f
        ),
        preset("bass_reducer", "Bass Reducer", -5.5f, -4.5f, -3.5f, -2.5f, -1f, 0f, 0f, 0f, 0f, 0f),
        preset(
            "classical",
            "Classical",
            4.5f,
            3.5f,
            3f,
            2.5f,
            -1.5f,
            -1.5f,
            0f,
            2f,
            3f,
            3.5f,
            preamp = -3.5f
        ),
        preset("dance", "Dance", 3.5f, 6.5f, 5f, 0f, 2f, 3.5f, 5f, 4.5f, 3.5f, 0f, preamp = -5f),
        preset("deep", "Deep", 5f, 3.5f, 1.5f, 1f, 3f, 2.5f, 1.5f, -2f, -3.5f, -4.5f, preamp = -4f),
        preset(
            "electronic",
            "Electronic",
            4f,
            3.5f,
            1f,
            0f,
            -2f,
            2f,
            1f,
            1f,
            4f,
            4.5f,
            preamp = -3.5f
        ),
        preset(
            "hip_hop",
            "Hip-Hop",
            5f,
            4.5f,
            1.5f,
            3f,
            -1f,
            -1f,
            1.5f,
            -0.5f,
            2f,
            3f,
            preamp = -4f
        ),
        preset("jazz", "Jazz", 4f, 3f, 1.5f, 2f, -1.5f, -1.5f, 0f, 1.5f, 3f, 3.5f, preamp = -3f),
        preset(
            "latin",
            "Latin",
            4.5f,
            3f,
            0f,
            0f,
            -1.5f,
            -1.5f,
            -1.5f,
            0f,
            3f,
            4.5f,
            preamp = -3.5f
        ),
        preset("loudness", "Loudness", 6f, 4f, 0f, 0f, -2f, 0f, -1f, -5f, 5f, 1f, preamp = -4.5f),
        preset(
            "lounge",
            "Lounge",
            -3f,
            -1.5f,
            -0.5f,
            1.5f,
            4f,
            2.5f,
            0f,
            -1.5f,
            2f,
            1f,
            preamp = -3f
        ),
        preset("piano", "Piano", 3f, 2f, 0f, 2.5f, 3f, 1.5f, 3.5f, 4.5f, 3f, 3.5f, preamp = -3.5f),
        preset("pop", "Pop", -1.5f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -1.5f, preamp = -3f),
        preset(
            "rnb",
            "R&B",
            2.5f,
            7f,
            5.5f,
            1.5f,
            -2.5f,
            -1.5f,
            2.5f,
            3f,
            3f,
            3.5f,
            preamp = -5.5f
        ),
        preset("rock", "Rock", 5f, 4f, 3f, 1.5f, -0.5f, -1f, 0.5f, 2.5f, 3.5f, 4.5f, preamp = -4f),
        preset(
            "small_speakers",
            "Small Speakers",
            5.5f,
            4f,
            3.5f,
            2.5f,
            1f,
            0f,
            -1f,
            -2.5f,
            -3.5f,
            -4f,
            preamp = -4f
        ),
        preset(
            "spoken_word",
            "Spoken Word",
            -3.5f,
            -0.5f,
            0f,
            0.5f,
            3.5f,
            4.5f,
            5f,
            4f,
            2.5f,
            0f,
            preamp = -4f
        ),
        preset(
            "treble_booster",
            "Treble Booster",
            0f,
            0f,
            0f,
            0f,
            0f,
            1f,
            2.5f,
            3.5f,
            4.5f,
            5.5f,
            preamp = -4f
        ),
        preset(
            "treble_reducer",
            "Treble Reducer",
            0f,
            0f,
            0f,
            0f,
            0f,
            -1f,
            -2.5f,
            -3.5f,
            -4.5f,
            -5.5f
        ),
        preset(
            "vocal_booster",
            "Vocal Booster",
            -1.5f,
            -3f,
            -3f,
            1.5f,
            3.5f,
            3.5f,
            3f,
            1.5f,
            0f,
            -1.5f,
            preamp = -3f
        ),
    )

    fun find(id: String?): TenBandPreset? = all.firstOrNull {
        it.id == id
    }
}

fun TenBandPreset.applyTo(settings: TenBandEqSettings): TenBandEqSettings = settings.copy(
    engine = EqEngine.BUILT_IN,
    gainsDb = gainsDb,
    preampDb = preampDb,
    profileName = name,
).normalized()
