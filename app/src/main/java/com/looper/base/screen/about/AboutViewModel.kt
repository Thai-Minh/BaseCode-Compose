package com.looper.base.screen.about

import com.looper.base.BuildConfig
import com.looper.base.R
import com.looper.base.base.mvi.MviViewModel
import com.looper.base.screen.about.data.AboutEffect
import com.looper.base.screen.about.data.AboutIntent
import com.looper.base.screen.about.data.AboutItem
import com.looper.base.screen.about.data.AboutUiState
import com.looper.base.screen.about.data.RatingStage

class AboutViewModel : MviViewModel<AboutUiState, AboutIntent, AboutEffect>(
    initialState = AboutUiState(versionName = BuildConfig.VERSION_NAME),
) {

    override fun onIntent(intent: AboutIntent) {
        when (intent) {
            AboutIntent.BackClick -> sendEffect(effect = AboutEffect.NavigateBack)

            is AboutIntent.ItemClick -> onItemClick(item = intent.item)

            is AboutIntent.RatingChange -> setState {
                copy(
                    rating = intent.rating.coerceIn(
                        minimumValue = 1,
                        maximumValue = AboutUiState.MAX_RATING
                    )
                )
            }

            is AboutIntent.ReasonToggle -> setState {
                copy(
                    selectedReasons = if (intent.reason in selectedReasons) {
                        selectedReasons - intent.reason
                    } else {
                        selectedReasons + intent.reason
                    }
                )
            }

            is AboutIntent.FeedbackChange -> setState {
                copy(feedback = intent.text)
            }

            AboutIntent.RateSubmit -> submitRating()

            AboutIntent.RatingDismiss -> resetRating()
        }
    }

    private fun onItemClick(item: AboutItem) {
        when (item) {
            AboutItem.Language -> sendEffect(effect = AboutEffect.NavigateToLanguage)
            AboutItem.ShareApp -> sendEffect(effect = AboutEffect.ShareApp)
            AboutItem.RateUs -> setState {
                copy(ratingStage = RatingStage.Rate)
            }

            AboutItem.PrivacyPolicy -> sendEffect(effect = AboutEffect.OpenUrl(urlRes = R.string.about_privacy_policy_url))
            AboutItem.TermsOfService -> sendEffect(effect = AboutEffect.OpenUrl(urlRes = R.string.about_terms_of_service_url))
        }
    }

    private fun submitRating() {
        val rating = currentState.rating
        if (rating <= 0) {
            return
        }
        setState {
            copy(ratingStage = RatingStage.Thanks)
        }
        if (rating >= AboutUiState.STORE_RATING_THRESHOLD) {
            sendEffect(effect = AboutEffect.OpenPlayStore)
        }
    }

    private fun resetRating() {
        setState {
            copy(
                ratingStage = null,
                rating = 0,
                selectedReasons = emptySet(),
                feedback = "",
            )
        }
    }
}
