package com.looper.base.player.model

import androidx.media3.common.Player
import kotlin.math.pow

object PlayerConstants {
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 3f
    const val MIN_PITCH_SEMITONES = -12f
    const val MAX_PITCH_SEMITONES = 12f

    val CACHE_SIZE_OPTIONS_MB: List<Int> = listOf(128, 256, 512, 1024, 2048)
    const val DEFAULT_CACHE_SIZE_MB = 512

    val VIDEO_HEIGHT_OPTIONS: List<Int> = listOf(360, 480, 720, 1080, 1440, 2160)
    const val DEFAULT_VIDEO_HEIGHT = 720
}

data class PlayerSettings(
    val playbackSpeed: Float = 1f,
    val pitchSemitones: Float = 0f,
    val skipSilence: Boolean = false,
    val cacheSizeMb: Int = PlayerConstants.DEFAULT_CACHE_SIZE_MB,
    val normalizeVolume: Boolean = false,
    val videoMaxHeight: Int = PlayerConstants.DEFAULT_VIDEO_HEIGHT,
    val preferredAudioLanguage: String? = null,
    val subtitleLanguage: String? = null,
    val stopOnTaskRemoved: Boolean = true,
    val audioQuality: AudioQuality = AudioQuality.AUTO,
) {
    val pitchMultiplier: Float
        get() = 2f.pow(x = pitchSemitones / 12f)

    fun normalized(): PlayerSettings = copy(
        playbackSpeed = playbackSpeed.coerceIn(
            minimumValue = PlayerConstants.MIN_SPEED,
            maximumValue = PlayerConstants.MAX_SPEED
        ),
        pitchSemitones = pitchSemitones.coerceIn(
            minimumValue = PlayerConstants.MIN_PITCH_SEMITONES,
            maximumValue = PlayerConstants.MAX_PITCH_SEMITONES
        ),
        cacheSizeMb = cacheSizeMb.takeIf {
            it in PlayerConstants.CACHE_SIZE_OPTIONS_MB
        } ?: PlayerConstants.DEFAULT_CACHE_SIZE_MB,
        videoMaxHeight = videoMaxHeight.takeIf {
            it in PlayerConstants.VIDEO_HEIGHT_OPTIONS
        } ?: PlayerConstants.DEFAULT_VIDEO_HEIGHT,
    )
}

data class PlaybackSnapshot(
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    @Player.RepeatMode val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
)
