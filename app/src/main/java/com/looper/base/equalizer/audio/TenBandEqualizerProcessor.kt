package com.looper.base.equalizer.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.looper.base.equalizer.model.TenBandEqConstants
import com.looper.base.equalizer.model.TenBandEqSettings
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

@OptIn(UnstableApi::class)
class TenBandEqualizerProcessor : BaseAudioProcessor() {

    private class Params(
        val enabled: Boolean,
        val settings: TenBandEqSettings,
    )

    private class Biquad(
        val b0: Float,
        val b1: Float,
        val b2: Float,
        val a1: Float,
        val a2: Float,
    )

    @Volatile
    private var params = Params(enabled = false, settings = TenBandEqSettings())

    private var appliedParams: Params? = null
    private var appliedSampleRate = 0
    private var filters: Array<Biquad> = emptyArray()
    private var preampGain = 1f
    private var state = FloatArray(size = 0)

    fun update(enabled: Boolean, settings: TenBandEqSettings) {
        params = Params(enabled = enabled, settings = settings.normalized())
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) {
            return
        }
        val output = replaceOutputBuffer(remaining)
        val current = params
        if (!current.enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        prepare(current = current)

        val channels = inputAudioFormat.channelCount
        val activeFilters = filters
        val gain = preampGain
        var channel = 0
        while (inputBuffer.remaining() >= 2) {
            var sample = inputBuffer.getShort() / 32768f * gain
            for (index in activeFilters.indices) {
                val filter = activeFilters[index]
                val offset = (index * channels + channel) * 2
                val output0 = filter.b0 * sample + state[offset]
                state[offset] = filter.b1 * sample - filter.a1 * output0 + state[offset + 1]
                state[offset + 1] = filter.b2 * sample - filter.a2 * output0
                sample = output0
            }
            output.putShort(
                (sample * 32767f).coerceIn(
                    minimumValue = -32768f,
                    maximumValue = 32767f
                ).toInt().toShort()
            )
            channel = (channel + 1) % channels
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        state.fill(element = 0f)
    }

    override fun onReset() {
        appliedParams = null
        appliedSampleRate = 0
        filters = emptyArray()
        state = FloatArray(size = 0)
    }

    private fun prepare(current: Params) {
        val sampleRate = inputAudioFormat.sampleRate
        if (current === appliedParams && sampleRate == appliedSampleRate) {
            return
        }
        val settings = current.settings
        filters = TenBandEqConstants.FREQUENCIES_HZ
            .mapIndexedNotNull { index, frequency ->
                val gainDb = settings.gainsDb[index]
                if (abs(x = gainDb) < 0.05f || frequency >= sampleRate / 2) {
                    null
                } else {
                    peaking(sampleRate = sampleRate, frequencyHz = frequency, gainDb = gainDb)
                }
            }
            .toTypedArray()
        preampGain = 10f.pow(x = settings.preampDb / 20f)
        val size = filters.size * inputAudioFormat.channelCount * 2
        if (state.size != size) {
            state = FloatArray(size)
        }
        appliedParams = current
        appliedSampleRate = sampleRate
    }

    private fun peaking(sampleRate: Int, frequencyHz: Int, gainDb: Float): Biquad {
        val amplitude = 10.0.pow(x = gainDb / 40.0)
        val omega = 2.0 * PI * frequencyHz / sampleRate
        val alpha = sin(x = omega) / (2.0 * TenBandEqConstants.Q)
        val cosOmega = cos(x = omega)
        val a0 = 1.0 + alpha / amplitude
        return Biquad(
            b0 = ((1.0 + alpha * amplitude) / a0).toFloat(),
            b1 = ((-2.0 * cosOmega) / a0).toFloat(),
            b2 = ((1.0 - alpha * amplitude) / a0).toFloat(),
            a1 = ((-2.0 * cosOmega) / a0).toFloat(),
            a2 = ((1.0 - alpha / amplitude) / a0).toFloat(),
        )
    }
}
