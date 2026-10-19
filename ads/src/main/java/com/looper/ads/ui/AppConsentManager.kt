package com.looper.ads.ui

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-facing Google UMP consent flow.
 *
 * This flow is deliberately independent from the existing initAd() flow. Consent results are used
 * for diagnostics and the required privacy-options entry point, never to disable existing ads.
 */
object AppConsentManager {
    private const val TAG = "AppConsentManager"

    private lateinit var consentInformation: ConsentInformation
    private var gatheringConsent = false

    val privacyOptionsRequired: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun initialize(context: Context) {
        if (!::consentInformation.isInitialized) {
            consentInformation = UserMessagingPlatform.getConsentInformation(
                context.applicationContext,
            )
        }
    }

    fun gatherConsent(activity: Activity) {
        initialize(activity)

        if (gatheringConsent) return

        gatheringConsent = true

        val parameters = ConsentRequestParameters.Builder().build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                updatePrivacyOptionsRequirement()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    gatheringConsent = false
                    updatePrivacyOptionsRequirement()
                    logResult("Consent flow completed", formError)
                }
            },
            { requestError ->
                gatheringConsent = false
                updatePrivacyOptionsRequirement()
                logResult("Consent info update failed", requestError)
            },
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        initialize(activity)
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            updatePrivacyOptionsRequirement()
            logResult("Privacy options closed", formError)
        }
    }

    private fun updatePrivacyOptionsRequirement() {
        privacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun logResult(event: String, error: FormError?) {
        val message = "$event: canRequestAds=${consentInformation.canRequestAds()}, " +
                "privacyOptionsRequired=${privacyOptionsRequired.value}"

        if (error == null) {
            Log.d(TAG, message)
        } else {
            Log.w(TAG, "$message, errorCode=${error.errorCode}, error=${error.message}")
        }
    }
}
