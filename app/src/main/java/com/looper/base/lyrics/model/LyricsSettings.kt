package com.looper.base.lyrics.model

import com.looper.base.database.data.LyricsProvider

data class LyricsSettings(
    val enabled: Boolean = true,
    val wordSync: Boolean = true,
    val useEmbedded: Boolean = true,
    val providerOrder: List<LyricsProvider> = LyricsProvider.DEFAULT_PRIORITY,
    val disabledProviders: Set<LyricsProvider> = LyricsProvider.REMOTE.filterNot {
        it.defaultEnabled
    }.toSet(),
    val offsetMs: Long = 0,
    val textSizeSp: Int = LyricsConstants.DEFAULT_TEXT_SIZE_SP,
    val tapToSeek: Boolean = true,
    val translationEnabled: Boolean = false,
    val translationLanguage: String? = null,
    val romanizationEnabled: Boolean = false,
    val translationExcludedLanguages: Set<String> = emptySet(),
    val romanizationExcludedLanguages: Set<String> = emptySet(),
    val blurInactiveLines: Boolean = true,
    val glowLongWords: Boolean = true,
) {
    val enabledProviders: List<LyricsProvider>
        get() = providerOrder.filterNot {
            it in disabledProviders
        }

    fun normalized(): LyricsSettings {
        val ordered = providerOrder.filter {
            it.isRemote
        }.distinct()
        val added = LyricsProvider.REMOTE.filterNot {
            it in ordered
        }
        return copy(
            providerOrder = ordered + added,
            disabledProviders = disabledProviders.filter {
                it.isRemote
            }.toSet() + added.filterNot {
                it.defaultEnabled
            },
            offsetMs = offsetMs.coerceIn(
                minimumValue = LyricsConstants.MIN_OFFSET_MS,
                maximumValue = LyricsConstants.MAX_OFFSET_MS
            ),
            textSizeSp = textSizeSp.coerceIn(
                minimumValue = LyricsConstants.MIN_TEXT_SIZE_SP,
                maximumValue = LyricsConstants.MAX_TEXT_SIZE_SP
            ),
        )
    }
}
