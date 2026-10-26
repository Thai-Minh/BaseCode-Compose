package com.looper.base.screen.language.data

import com.looper.base.R


enum class LanguageType(
    val iconId: Int,
    val iconDisableId: Int,
    val languageResId: Int,
    val nationResId: Int? = null,
    val localize: String
) {
    Default(
        iconId = R.drawable.ic_language_default,
        iconDisableId = R.drawable.ic_language_default_disable,
        languageResId = R.string.system_default_language,
        localize = ""
    ),
    Spanish(
        iconId = R.drawable.ic_flag_espanol,
        iconDisableId = R.drawable.ic_flag_espanol_disable,
        languageResId = R.string.language_espanol,
        nationResId = R.string.nation_espanol,
        localize = "es"
    ),
    Vietnamese(
        iconId = R.drawable.ic_flag_vn,
        iconDisableId = R.drawable.ic_flag_vn_disable,
        languageResId = R.string.language_vn,
        nationResId = R.string.nation_vn,
        localize = "vi"
    ),
    English(
        iconId = R.drawable.ic_flag_english,
        iconDisableId = R.drawable.ic_flag_english_disable,
        languageResId = R.string.language_english,
        nationResId = R.string.nation_english,
        localize = "en"
    ),
    France(
        iconId = R.drawable.ic_flag_france,
        iconDisableId = R.drawable.ic_flag_france_disable,
        languageResId = R.string.language_france,
        nationResId = R.string.nation_france,
        localize = "fr"
    ),
    India(
        iconId = R.drawable.ic_flag_india,
        iconDisableId = R.drawable.ic_flag_india_disable,
        languageResId = R.string.language_india,
        nationResId = R.string.nation_india,
        localize = "hi"
    ),
    Portuguese(
        iconId = R.drawable.ic_flag_portugal,
        iconDisableId = R.drawable.ic_flag_portugal_disable,
        languageResId = R.string.language_portugal,
        nationResId = R.string.nation_portugal,
        localize = "pt"
    ),
    Deutsch(
        iconId = R.drawable.ic_flag_germany,
        iconDisableId = R.drawable.ic_flag_germany_disable,
        languageResId = R.string.language_deutsch,
        nationResId = R.string.nation_deutsch,
        localize = "de"
    ),
    Uae(
        iconId = R.drawable.ic_flag_uae,
        iconDisableId = R.drawable.ic_flag_uae_disable,
        languageResId = R.string.language_uae,
        nationResId = R.string.nation_uae,
        localize = "ar"
    ),
    Africa(
        iconId = R.drawable.ic_flag_africa,
        iconDisableId = R.drawable.ic_flag_africa_disable,
        languageResId = R.string.language_africa,
        nationResId = R.string.nation_africa,
        localize = "af"
    ),
    China(
        iconId = R.drawable.ic_flag_china,
        iconDisableId = R.drawable.ic_flag_china_disable,
        languageResId = R.string.language_china,
        nationResId = R.string.nation_china,
        localize = "zh"
    ),
    Italia(
        iconId = R.drawable.ic_flag_italia,
        iconDisableId = R.drawable.ic_flag_italia_disable,
        languageResId = R.string.language_italia,
        nationResId = R.string.nation_italia,
        localize = "it"
    ),
    Korea(
        iconId = R.drawable.ic_flag_korea,
        iconDisableId = R.drawable.ic_flag_korea_disable,
        languageResId = R.string.language_korea,
        nationResId = R.string.nation_korea,
        localize = "ko"
    ),
    Poland(
        iconId = R.drawable.ic_flag_poland,
        iconDisableId = R.drawable.ic_flag_poland_disable,
        languageResId = R.string.language_poland,
        nationResId = R.string.nation_poland,
        localize = "pl"
    ),
    Japan(
        iconId = R.drawable.ic_flag_japan,
        iconDisableId = R.drawable.ic_flag_japan_disable,
        languageResId = R.string.language_japan,
        nationResId = R.string.nation_japan,
        localize = "ja"
    ),
    Malaysia(
        iconId = R.drawable.ic_flag_malaysia,
        iconDisableId = R.drawable.ic_flag_malaysia_disable,
        languageResId = R.string.language_malaysia,
        nationResId = R.string.nation_malaysia,
        localize = "ms"
    ),
    Canada(
        iconId = R.drawable.ic_flag_canada,
        iconDisableId = R.drawable.ic_flag_canada_disable,
        languageResId = R.string.language_canada,
        nationResId = R.string.nation_canada,
        localize = "en-CA"
    ),
    Indonesia(
        iconId = R.drawable.ic_flag_indo,
        iconDisableId = R.drawable.ic_flag_indo_disable,
        languageResId = R.string.language_indo,
        nationResId = R.string.nation_indo,
        localize = "in"
    ),
    Thai(
        iconId = R.drawable.ic_flag_thailand,
        iconDisableId = R.drawable.ic_flag_thailand_disable,
        languageResId = R.string.language_thai,
        nationResId = R.string.nation_thai,
        localize = "th"
    ),
    Turkey(
        iconId = R.drawable.ic_flag_turkey,
        iconDisableId = R.drawable.ic_flag_turkey_disable,
        languageResId = R.string.language_turkey,
        nationResId = R.string.nation_turkey,
        localize = "tr"
    ),
    Russia(
        iconId = R.drawable.ic_flag_russia,
        iconDisableId = R.drawable.ic_flag_russia_disable,
        languageResId = R.string.language_russia,
        nationResId = R.string.nation_russia,
        localize = "ru"
    ),
    Uzbekistan(
        iconId = R.drawable.ic_flag_uzbekistan,
        iconDisableId = R.drawable.ic_flag_uzbekistan_disable,
        languageResId = R.string.language_uzbe,
        nationResId = R.string.nation_uzbe,
        localize = "uz"
    ),
    India2(
        iconId = R.drawable.ic_flag_india,
        iconDisableId = R.drawable.ic_flag_india_disable,
        languageResId = R.string.language_india_2,
        nationResId = R.string.nation_india_2,
        localize = "ur"
    );

    val isSystemDefault: Boolean
        get() = this == Default

    companion object {
        fun findByLocalize(value: String): LanguageType {
            if (value.isEmpty()) return Default

            return entries.find {
                it.localize == value
            } ?: Default
        }
    }
}

data class LanguageItem(
    val type: LanguageType,
    val isEnable: Boolean = false,
    val isSelected: Boolean = false,
)

fun languageDefaultItems(): List<LanguageItem> {
    return LanguageType.entries.map {
        LanguageItem(type = it)
    }
}