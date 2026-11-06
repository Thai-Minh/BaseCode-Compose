package com.looper.base.screen.about.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.looper.base.R
import com.looper.base.base.mvi.UiEffect
import com.looper.base.base.mvi.UiIntent
import com.looper.base.base.mvi.UiState

enum class AboutItem(
    @DrawableRes val iconRes: Int,
    @StringRes val labelRes: Int,
) {
    Language(
        iconRes = R.drawable.ic_about_language,
        labelRes = R.string.about_language,
    ),
    ShareApp(
        iconRes = R.drawable.ic_about_share,
        labelRes = R.string.about_share_app,
    ),
    RateUs(
        iconRes = R.drawable.ic_about_rate,
        labelRes = R.string.about_rate_us,
    ),
    PrivacyPolicy(
        iconRes = R.drawable.ic_about_privacy,
        labelRes = R.string.about_privacy_policy,
    ),
    TermsOfService(
        iconRes = R.drawable.ic_about_terms,
        labelRes = R.string.about_terms_of_service,
    ),
}

enum class FeedbackReason(
    @StringRes val labelRes: Int,
) {
    Crashes(labelRes = R.string.rating_reason_crashes),
    Confusing(labelRes = R.string.rating_reason_confusing),
    Interface(labelRes = R.string.rating_reason_interface),
    Ads(labelRes = R.string.rating_reason_ads),
    Other(labelRes = R.string.rating_reason_other),
}

enum class RatingStage {
    Rate,
    Thanks,
}

data class AboutUiState(
    val versionName: String = "",
    val items: List<AboutItem> = AboutItem.entries,
    val ratingStage: RatingStage? = null,
    val rating: Int = 0,
    val reasons: List<FeedbackReason> = FeedbackReason.entries,
    val selectedReasons: Set<FeedbackReason> = emptySet(),
    val feedback: String = "",
) : UiState {
    val isLowRating: Boolean
        get() = rating in 1 until STORE_RATING_THRESHOLD

    val showsFeedbackField: Boolean
        get() = isLowRating && FeedbackReason.Other in selectedReasons

    companion object {
        const val MAX_RATING = 5
        const val STORE_RATING_THRESHOLD = 4
    }
}

sealed interface AboutIntent : UiIntent {
    data object BackClick : AboutIntent
    data class ItemClick(val item: AboutItem) : AboutIntent
    data class RatingChange(val rating: Int) : AboutIntent
    data class ReasonToggle(val reason: FeedbackReason) : AboutIntent
    data class FeedbackChange(val text: String) : AboutIntent
    data object RateSubmit : AboutIntent
    data object RatingDismiss : AboutIntent
}

sealed interface AboutEffect : UiEffect {
    data object NavigateBack : AboutEffect
    data object NavigateToLanguage : AboutEffect
    data object ShareApp : AboutEffect
    data object OpenPlayStore : AboutEffect
    data class OpenUrl(@StringRes val urlRes: Int) : AboutEffect
}
