package com.looper.base.equalizer.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.looper.base.equalizer.model.AudioEffectsConstants
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
class EchoProcessor : BaseAudioProcessor() {

    private class Params(
        val enabled: Boolean,
        val delayMs: Int,
        val feedback: Float,
        val mix: Float,
    )

    @Volatile
    private var params = Params(enabled = false, delayMs = 0, feedback = 0f, mix = 0f)

    private var buffer = FloatArray(size = 0)
    private var writeIndex = 0

    fun update(enabled: Boolean, delayMs: Int, feedback: Float, mix: Float) {
        params = Params(enabled = enabled, delayMs = delayMs, feedback = feedback, mix = mix)
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
        if (!current.enabled || current.mix <= 0f) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val channels = inputAudioFormat.channelCount
        val maxLength =
            inputAudioFormat.sampleRate * channels * AudioEffectsConstants.MAX_ECHO_DELAY_MS / 1000
        if (buffer.size != maxLength) {
            buffer = FloatArray(size = maxLength)
            writeIndex = 0
        }
        val delaySamples = (inputAudioFormat.sampleRate * current.delayMs / 1000 * channels)
            .coerceIn(minimumValue = channels, maximumValue = maxLength - channels)
        while (inputBuffer.remaining() >= 2) {
            val dry = inputBuffer.getShort() / 32768f
            val readIndex = (writeIndex - delaySamples + buffer.size) % buffer.size
            val delayed = buffer[readIndex]
            buffer[writeIndex] = dry + delayed * current.feedback
            writeIndex = (writeIndex + 1) % buffer.size
            val wet = dry * (1f - current.mix) + delayed * current.mix
            output.putShort(
                (wet * 32767f).coerceIn(minimumValue = -32768f, maximumValue = 32767f).toInt()
                    .toShort()
            )
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        buffer.fill(element = 0f)
        writeIndex = 0
    }

    override fun onReset() {
        buffer = FloatArray(0)
        writeIndex = 0
    }
}
