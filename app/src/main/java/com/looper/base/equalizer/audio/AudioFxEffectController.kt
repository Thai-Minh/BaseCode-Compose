package com.looper.base.equalizer.audio

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.looper.base.equalizer.model.EqBandMapper
import com.looper.base.equalizer.model.EqCapabilities
import com.looper.base.equalizer.model.EqConstants
import com.looper.base.equalizer.model.EqSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioFxEffectController : AudioEffectController {

    private companion object {
        const val TAG = "AudioFxEffect"
        const val PRIORITY = 0
    }

    private val _capabilities =
        MutableStateFlow(EqCapabilities.unattached(supported = isEqualizerAvailable()))
    override val capabilities: StateFlow<EqCapabilities> = _capabilities.asStateFlow()

    private val _audioSessionId = MutableStateFlow(EqConstants.AUDIO_SESSION_UNSET)
    override val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null

    private var deviceFreqsHz: List<Int> = emptyList()
    private var deviceMinMb: Int = EqConstants.MIN_LEVEL_MB
    private var deviceMaxMb: Int = EqConstants.MAX_LEVEL_MB

    private var lastSettings: EqSettings = EqSettings()

    @Synchronized
    override fun attach(audioSessionId: Int) {
        if (audioSessionId == EqConstants.AUDIO_SESSION_UNSET) {
            return
        }
        if (audioSessionId == _audioSessionId.value && equalizer != null) {
            return
        }

        releaseEffects()
        _audioSessionId.value = audioSessionId

        val eq = try {
            Equalizer(PRIORITY, audioSessionId)
        } catch (t: Throwable) {
            Log.w(TAG, "Equalizer unavailable on session $audioSessionId", t)
            _capabilities.value = EqCapabilities.unattached(supported = false)
            return
        }
        equalizer = eq

        try {
            val bandCount = eq.numberOfBands.toInt()
            deviceFreqsHz =
                (0 until bandCount).map {
                    eq.getCenterFreq(it.toShort()) / 1000
                }
            val range = eq.bandLevelRange
            deviceMinMb = range[0].toInt()
            deviceMaxMb = range[1].toInt()
            _capabilities.value = EqCapabilities(
                supported = true,
                attached = true,
                bandCount = bandCount,
                centerFreqHz = deviceFreqsHz,
                minLevelMb = deviceMinMb,
                maxLevelMb = deviceMaxMb,
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read equalizer capabilities, assuming standard layout", t)
            deviceFreqsHz = EqConstants.STANDARD_FREQS_HZ
            _capabilities.value = EqCapabilities.unattached(supported = true).copy(attached = true)
        }

        bassBoost = runCatching {
            BassBoost(PRIORITY, audioSessionId)
        }.onFailure {
            Log.w(TAG, "BassBoost unavailable", it)
        }.getOrNull()
        virtualizer = runCatching {
            Virtualizer(PRIORITY, audioSessionId)
        }.onFailure {
            Log.w(TAG, "Virtualizer unavailable", it)
        }.getOrNull()
        loudness = runCatching {
            LoudnessEnhancer(audioSessionId)
        }.onFailure {
            Log.w(TAG, "LoudnessEnhancer unavailable", it)
        }.getOrNull()

        apply(lastSettings)
    }

    @Synchronized
    override fun apply(settings: EqSettings) {
        val normalized = settings.normalized()
        lastSettings = normalized

        val eq = equalizer ?: return
        val enabled = normalized.enabled

        runCatching {
            eq.enabled = enabled
            val levels = EqBandMapper.mapToDevice(
                standardLevelsMb = normalized.bandLevelsMb,
                deviceFreqsHz = deviceFreqsHz
            )
            levels.forEachIndexed { index, levelMb ->
                eq.setBandLevel(
                    index.toShort(),
                    levelMb.coerceIn(minimumValue = deviceMinMb, maximumValue = deviceMaxMb)
                        .toShort()
                )
            }
        }.onFailure {
            Log.w(TAG, "Failed to apply equalizer bands", it)
        }

        bassBoost?.let { effect ->
            runCatching {
                effect.setStrength(normalized.bassBoost.toShort())
                effect.enabled = enabled && normalized.bassBoost > 0
            }.onFailure {
                Log.w(TAG, "Failed to apply bass boost", it)
            }
        }

        virtualizer?.let { effect ->
            runCatching {
                effect.setStrength(normalized.virtualizer.toShort())
                effect.enabled = enabled && normalized.virtualizer > 0
            }.onFailure {
                Log.w(TAG, "Failed to apply virtualizer", it)
            }
        }

        loudness?.let { effect ->
            runCatching {
                effect.setTargetGain(normalized.loudnessGainMb)
                effect.enabled = enabled && normalized.loudnessGainMb > 0
            }.onFailure {
                Log.w(TAG, "Failed to apply loudness", it)
            }
        }
    }

    @Synchronized
    override fun detach() {
        releaseEffects()
        _audioSessionId.value = EqConstants.AUDIO_SESSION_UNSET
        _capabilities.value = EqCapabilities.unattached(supported = _capabilities.value.supported)
    }

    private fun releaseEffects() {
        listOf(equalizer, bassBoost, virtualizer, loudness).forEach { effect ->
            runCatching {
                effect?.release()
            }
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        deviceFreqsHz = emptyList()
    }

    private fun isEqualizerAvailable(): Boolean = runCatching {
        AudioEffect.queryEffects()?.any {
            it.type == AudioEffect.EFFECT_TYPE_EQUALIZER
        } ?: false
    }.getOrDefault(defaultValue = false)
}
