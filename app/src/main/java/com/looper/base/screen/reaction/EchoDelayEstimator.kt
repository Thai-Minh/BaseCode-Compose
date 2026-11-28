package com.looper.base.screen.reaction

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

private const val TAG = "EchoDelayEstimator"

/** Sample rate of the analysis: enough for speech and music to correlate, and cheap. */
private const val ANALYSIS_RATE = 8_000

/** How much of the tracks is compared, from their start. */
private const val ANALYSIS_US = 30_000_000L

/** Searched delays: the speaker's output latency, from a little early to well late. */
private const val MIN_DELAY_US = -100_000L
private const val MAX_DELAY_US = 600_000L

/** How many standard deviations the correlation peak must stand out by to be trusted. */
private const val MIN_PEAK_SCORE = 10.0

private const val CODEC_TIMEOUT_US = 10_000L

/** A decoder that outputs nothing for this many timeouts after its input ended is given up on. */
private const val MAX_IDLE_DRAINS = 100

/**
 * Finds how late the background's sound reached the microphone in a reaction recording.
 *
 * The background starts playing when the recording starts. Its sound leaves the speaker a bit
 * later (the output latency, typically 50 to 250 ms), and the microphone records that copy too,
 * unless the echo canceller took it out. Mixed with the background's own track, that late copy
 * is heard as an echo. Cutting that much from the start of the reaction moves the copy on top of
 * the background's own sound, where it is no longer heard as an echo. It also lines the reaction
 * up with what the user actually heard.
 *
 * Compares the first [ANALYSIS_US] of both tracks, decoded to mono at [ANALYSIS_RATE] Hz, with
 * [GccPhat].
 */
internal class EchoDelayEstimator(private val context: Context) {

    /**
     * The delay in microseconds (positive: the recorded copy is late). Returns null when the
     * [recording] has no clear copy of the [background]: none was recorded, the echo canceller
     * removed it, or a track has no audio.
     */
    suspend fun estimateUs(background: Uri, recording: Uri): Long? =
        withContext(Dispatchers.Default) {
            val reference = decodeMono(background, ANALYSIS_US) ?: return@withContext null
            val recorded = decodeMono(recording, ANALYSIS_US + MAX_DELAY_US)
                ?: return@withContext null
            val lag = GccPhat.estimateLag(
                reference = reference,
                recorded = recorded,
                minLag = usToSamples(MIN_DELAY_US),
                maxLag = usToSamples(MAX_DELAY_US),
                minPeakScore = MIN_PEAK_SCORE,
            )
            val delayUs = lag?.let { it * 1_000_000L / ANALYSIS_RATE }
            val found = delayUs?.let { "${it / 1000} ms late" } ?: "none"
            Log.i(TAG, "Background echo in the reaction: $found")
            delayUs
        }

    /**
     * The first audio track of [uri] from 0 to [durationUs], mixed to mono and placed by
     * presentation time. Input samples are summed into each [ANALYSIS_RATE] sample, a crude
     * low-pass that is enough to correlate. Returns null without an audio track, or if it can't
     * be decoded.
     */
    private suspend fun decodeMono(uri: Uri, durationUs: Long): FloatArray? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val codec = MediaCodec.createDecoderByType(
                checkNotNull(format.getString(MediaFormat.KEY_MIME))
            )
            decoder = codec
            codec.configure(format, null, null, 0)
            codec.start()

            val mono = FloatArray(usToSamples(durationUs))
            var pcm = PcmFormat.of(format) ?: return null
            val info = MediaCodec.BufferInfo()
            var inputEnded = false
            var idleDrains = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val index = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (index >= 0) {
                        val input = checkNotNull(codec.getInputBuffer(index))
                        val size = extractor.readSampleData(input, 0)
                        val timeUs = extractor.sampleTime
                        if (size < 0 || timeUs > durationUs) {
                            codec.queueInputBuffer(
                                index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(index, 0, size, timeUs, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
                when {
                    index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                        pcm = PcmFormat.of(codec.outputFormat) ?: return null

                    index >= 0 -> {
                        idleDrains = 0
                        val output = codec.getOutputBuffer(index)
                        if (output != null && info.size > 0) {
                            output.limit(info.offset + info.size)
                            output.position(info.offset)
                            addToMono(output, pcm, info.presentationTimeUs, mono)
                        }
                        codec.releaseOutputBuffer(index, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return mono
                    }

                    inputEnded && ++idleDrains > MAX_IDLE_DRAINS -> return mono
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Can't decode the audio of $uri", e)
            return null
        } finally {
            decoder?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    /** Adds the interleaved [samples] (the first one at [presentationTimeUs]) to [mono]. */
    private fun addToMono(
        samples: ByteBuffer,
        format: PcmFormat,
        presentationTimeUs: Long,
        mono: FloatArray,
    ) {
        val pcm = samples.order(ByteOrder.nativeOrder())
        val bytesPerSample = if (format.isFloat) 4 else 2
        val frameBytes = bytesPerSample * format.channelCount
        val frames = pcm.remaining() / frameBytes
        val base = pcm.position()
        val firstIndex = presentationTimeUs.toDouble() * ANALYSIS_RATE / 1_000_000
        val indexStep = ANALYSIS_RATE.toDouble() / format.sampleRate
        for (frame in 0 until frames) {
            val index = floor(firstIndex + frame * indexStep).toInt()
            if (index < 0) continue
            if (index >= mono.size) return
            var sum = 0f
            for (channel in 0 until format.channelCount) {
                val offset = base + frame * frameBytes + channel * bytesPerSample
                sum += if (format.isFloat) pcm.getFloat(offset) else pcm.getShort(offset) / 32_768f
            }
            mono[index] += sum
        }
    }

    private fun usToSamples(us: Long): Int = (us * ANALYSIS_RATE / 1_000_000).toInt()

    /** Decoded audio: 16-bit (the decoders' default) or float PCM. */
    private class PcmFormat(val sampleRate: Int, val channelCount: Int, val isFloat: Boolean) {
        companion object {
            /** Null for another sample encoding, or without a sample rate / channel count. */
            fun of(format: MediaFormat): PcmFormat? {
                val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
                if (encoding != AudioFormat.ENCODING_PCM_16BIT &&
                    encoding != AudioFormat.ENCODING_PCM_FLOAT
                ) {
                    return null
                }
                val sampleRate = format.getIntegerOrZero(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = format.getIntegerOrZero(MediaFormat.KEY_CHANNEL_COUNT)
                if (sampleRate <= 0 || channelCount <= 0) return null
                return PcmFormat(sampleRate, channelCount, encoding == AudioFormat.ENCODING_PCM_FLOAT)
            }

            private fun MediaFormat.getIntegerOrZero(key: String): Int =
                if (containsKey(key)) getInteger(key) else 0
        }
    }
}
