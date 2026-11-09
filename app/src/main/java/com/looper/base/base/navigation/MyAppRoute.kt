package com.looper.base.base.navigation

import kotlinx.serialization.Serializable


@Serializable
object SplashInput

@Serializable
data class LanguageInput(
    val openFrom: String
)

@Serializable
object OnBoardingInput

@Serializable
object MainInput

@Serializable
object EqualizerInput

@Serializable
object SleepTimerInput

@Serializable
object SocialAppInput

@Serializable
object AboutInput
