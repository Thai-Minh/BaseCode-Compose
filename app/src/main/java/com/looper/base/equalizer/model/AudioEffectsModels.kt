package com.looper.base.equalizer.model

enum class ReverbPreset(
    val roomSize: Float,
    val damping: Float,
    val width: Float,
) {
    ROOM(roomSize = 0.45f, damping = 0.5f, width = 0.8f),
    HALL(roomSize = 0.75f, damping = 0.35f, width = 1f),
    PLATE(roomSize = 0.6f, damping = 0.15f, width = 1f),
    CATHEDRAL(roomSize = 0.92f, damping = 0.25f, width = 1f),
}

enum class EchoPreset(
    val delayMs: Int,
    val feedback: Float,
    val mix: Float,
) {
    SLAPBACK(delayMs = 110, feedback = 0.15f, mix = 0.3f),
    QUARTER(delayMs = 375, feedback = 0.35f, mix = 0.3f),
    DUB(delayMs = 500, feedback = 0.6f, mix = 0.4f),
}

object AudioEffectsConstants {
    const val MIN_ECHO_DELAY_MS = 20
    const val MAX_ECHO_DELAY_MS = 1_000
    const val MAX_ECHO_FEEDBACK = 0.9f
}

data class AudioEffectsSettings(
    val echoEnabled: Boolean = false,
    val echoDelayMs: Int = EchoPreset.QUARTER.delayMs,
    val echoFeedback: Float = EchoPreset.QUARTER.feedback,
    val echoMix: Float = EchoPreset.QUARTER.mix,
    val reverbEnabled: Boolean = false,
    val reverbPreset: ReverbPreset = ReverbPreset.ROOM,
    val reverbMix: Float = 0.3f,
) {
    fun normalized(): AudioEffectsSettings = copy(
        echoDelayMs = echoDelayMs.coerceIn(
            minimumValue = AudioEffectsConstants.MIN_ECHO_DELAY_MS,
            maximumValue = AudioEffectsConstants.MAX_ECHO_DELAY_MS
        ),
        echoFeedback = echoFeedback.coerceIn(
            minimumValue = 0f,
            maximumValue = AudioEffectsConstants.MAX_ECHO_FEEDBACK
        ),
        echoMix = echoMix.coerceIn(minimumValue = 0f, maximumValue = 1f),
        reverbMix = reverbMix.coerceIn(minimumValue = 0f, maximumValue = 1f),
    )

    fun withEcho(preset: EchoPreset): AudioEffectsSettings = copy(
        echoEnabled = true,
        echoDelayMs = preset.delayMs,
        echoFeedback = preset.feedback,
        echoMix = preset.mix,
    )
}
