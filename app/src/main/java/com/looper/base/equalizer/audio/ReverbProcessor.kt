package com.looper.base.equalizer.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.looper.base.equalizer.model.ReverbPreset
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
class ReverbProcessor : BaseAudioProcessor() {

    private companion object {
        val COMB_TUNINGS = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
        val ALLPASS_TUNINGS = intArrayOf(556, 441, 341, 225)
        const val STEREO_SPREAD = 23
        const val REFERENCE_RATE = 44_100f
        const val INPUT_GAIN = 0.015f
        const val ALLPASS_FEEDBACK = 0.5f
    }

    private class Params(
        val enabled: Boolean,
        val preset: ReverbPreset,
        val mix: Float,
    )

    private class Comb(size: Int) {
        val buffer = FloatArray(size = size)
        var index = 0
        var filterStore = 0f

        fun process(input: Float, feedback: Float, damping: Float): Float {
            val output = buffer[index]
            filterStore = output * (1f - damping) + filterStore * damping
            buffer[index] = input + filterStore * feedback
            index = (index + 1) % buffer.size
            return output
        }

        fun clear() {
            buffer.fill(element = 0f)
            filterStore = 0f
        }
    }

    private class Allpass(size: Int) {
        val buffer = FloatArray(size = size)
        var index = 0

        fun process(input: Float): Float {
            val buffered = buffer[index]
            buffer[index] = input + buffered * ALLPASS_FEEDBACK
            index = (index + 1) % buffer.size
            return buffered - input
        }

        fun clear() {
            buffer.fill(element = 0f)
        }
    }

    @Volatile
    private var params = Params(enabled = false, preset = ReverbPreset.ROOM, mix = 0f)

    private var combs: Array<Array<Comb>> = emptyArray()
    private var allpasses: Array<Array<Allpass>> = emptyArray()
    private var builtForRate = 0

    fun update(enabled: Boolean, preset: ReverbPreset, mix: Float) {
        params = Params(enabled = enabled, preset = preset, mix = mix)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount in 1..2) {
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
        if (!current.enabled || current.mix <= 0f) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        ensureFilters(sampleRate = inputAudioFormat.sampleRate)
        val channels = inputAudioFormat.channelCount
        val feedback = current.preset.roomSize * 0.28f + 0.7f
        val damping = current.preset.damping * 0.4f
        val wet1 = current.mix * (current.preset.width / 2f + 0.5f)
        val wet2 = current.mix * ((1f - current.preset.width) / 2f)
        val dryGain = 1f - current.mix * 0.5f
        while (inputBuffer.remaining() >= channels * 2) {
            val left = inputBuffer.getShort() / 32768f
            val right = if (channels == 2) {
                inputBuffer.getShort() / 32768f
            } else {
                left
            }
            val input = (left + right) * INPUT_GAIN
            var outLeft = 0f
            var outRight = 0f
            for (index in COMB_TUNINGS.indices) {
                outLeft += combs[0][index].process(
                    input = input,
                    feedback = feedback,
                    damping = damping
                )
                outRight += combs[1][index].process(
                    input = input,
                    feedback = feedback,
                    damping = damping
                )
            }
            for (index in ALLPASS_TUNINGS.indices) {
                outLeft = allpasses[0][index].process(input = outLeft)
                outRight = allpasses[1][index].process(input = outRight)
            }
            val mixedLeft = left * dryGain + outLeft * wet1 + outRight * wet2
            output.putShort(toPcm(value = mixedLeft))
            if (channels == 2) {
                val mixedRight = right * dryGain + outRight * wet1 + outLeft * wet2
                output.putShort(toPcm(value = mixedRight))
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        combs.forEach { side ->
            side.forEach(action = Comb::clear)
        }
        allpasses.forEach { side ->
            side.forEach(action = Allpass::clear)
        }
    }

    override fun onReset() {
        combs = emptyArray()
        allpasses = emptyArray()
        builtForRate = 0
    }

    private fun ensureFilters(sampleRate: Int) {
        if (builtForRate == sampleRate && combs.isNotEmpty()) {
            return
        }
        val scale = sampleRate / REFERENCE_RATE
        combs = Array(size = 2) { side ->
            Array(COMB_TUNINGS.size) { index ->
                Comb(
                    size = ((COMB_TUNINGS[index] + side * STEREO_SPREAD) * scale).toInt()
                        .coerceAtLeast(minimumValue = 1)
                )
            }
        }
        allpasses = Array(size = 2) { side ->
            Array(ALLPASS_TUNINGS.size) { index ->
                Allpass(
                    size = ((ALLPASS_TUNINGS[index] + side * STEREO_SPREAD) * scale).toInt()
                        .coerceAtLeast(minimumValue = 1)
                )
            }
        }
        builtForRate = sampleRate
    }

    private fun toPcm(value: Float): Short =
        (value * 32767f).coerceIn(minimumValue = -32768f, maximumValue = 32767f).toInt().toShort()
}
