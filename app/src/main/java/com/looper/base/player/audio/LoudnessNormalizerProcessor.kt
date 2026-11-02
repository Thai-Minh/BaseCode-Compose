package com.looper.base.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.tanh

@OptIn(UnstableApi::class)
class LoudnessNormalizerProcessor : BaseAudioProcessor() {

    private companion object {
        const val TARGET_DB = -14f
        const val MAX_BOOST_DB = 8f
        const val MAX_CUT_DB = -12f
        const val BLOCK_SECONDS = 0.4f
        const val SILENCE_GATE_DB = -60.0
        const val MIN_MEASURED_SECONDS = 20f
        const val GAIN_SMOOTHING_SECONDS = 3f
        const val LIMIT_THRESHOLD = 0.89f
    }

    @Volatile
    var enabled: Boolean = false

    @Volatile
    private var knownLoudnessDb: Float? = null

    @Volatile
    private var trackGeneration = 0

    private var appliedGeneration = -1
    private var blockSquareSum = 0.0
    private var blockSamples = 0
    private var gatedEnergySum = 0.0
    private var gatedBlocks = 0
    private var currentGain = 1f
    private var gainStep = 0f

    @Volatile
    private var measuredLoudnessDb: Float? = null

    fun startTrack(knownLoudnessDb: Float?) {
        this.knownLoudnessDb = knownLoudnessDb
        measuredLoudnessDb = null
        trackGeneration++
    }

    fun measuredLoudness(): Float? = measuredLoudnessDb

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
        if (!enabled) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val generation = trackGeneration
        if (generation != appliedGeneration) {
            resetMeasurement()
            appliedGeneration = generation
        }

        val format = inputAudioFormat
        val samplesPerBlock = (format.sampleRate * format.channelCount * BLOCK_SECONDS).toInt()
            .coerceAtLeast(minimumValue = 1)
        val smoothingSamples = format.sampleRate * format.channelCount * GAIN_SMOOTHING_SECONDS
        gainStep = (1f - exp(x = -1f / smoothingSamples))

        var target = targetGain()
        while (inputBuffer.remaining() >= 2) {
            val sample = inputBuffer.getShort() / 32768f
            blockSquareSum += (sample * sample).toDouble()
            blockSamples++
            if (blockSamples >= samplesPerBlock) {
                closeBlock()
                target = targetGain()
            }
            currentGain += (target - currentGain) * gainStep
            output.putShort((limit(value = sample * currentGain) * 32767f).toInt().toShort())
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        blockSquareSum = 0.0
        blockSamples = 0
    }

    override fun onReset() {
        resetMeasurement()
        appliedGeneration = -1
        currentGain = 1f
    }

    private fun resetMeasurement() {
        blockSquareSum = 0.0
        blockSamples = 0
        gatedEnergySum = 0.0
        gatedBlocks = 0
    }

    private fun closeBlock() {
        val meanSquare = blockSquareSum / blockSamples
        blockSquareSum = 0.0
        blockSamples = 0
        if (meanSquare <= 0.0 || 10.0 * log10(x = meanSquare) < SILENCE_GATE_DB) {
            return
        }
        gatedEnergySum += meanSquare
        gatedBlocks++
        if (gatedBlocks * BLOCK_SECONDS >= MIN_MEASURED_SECONDS) {
            measuredLoudnessDb = (10.0 * log10(x = gatedEnergySum / gatedBlocks)).toFloat()
        }
    }

    private fun targetGain(): Float {
        val loudness = knownLoudnessDb
            ?: measuredLoudnessDb
            ?: gatedBlocks.takeIf {
                it * BLOCK_SECONDS >= 3f
            }?.let {
                (10.0 * log10(x = gatedEnergySum / it)).toFloat()
            } ?: return 1f
        val gainDb =
            (TARGET_DB - loudness).coerceIn(minimumValue = MAX_CUT_DB, maximumValue = MAX_BOOST_DB)
        return 10f.pow(x = gainDb / 20f)
    }

    private fun limit(value: Float): Float {
        val magnitude = abs(x = value)
        if (magnitude <= LIMIT_THRESHOLD) {
            return value
        }
        val headroom = 1f - LIMIT_THRESHOLD
        return sign(x = value) * (LIMIT_THRESHOLD + headroom * tanh(x = (magnitude - LIMIT_THRESHOLD) / headroom))
    }
}
