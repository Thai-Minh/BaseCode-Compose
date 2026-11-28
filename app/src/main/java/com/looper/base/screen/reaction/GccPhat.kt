package com.looper.base.screen.reaction

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds the delay of a signal's copy inside another one with GCC-PHAT. The steps:
 * - Average the cross-spectrum of the two over half-overlapping, Hann-windowed segments
 *   (Welch's method).
 * - Keep only its phase (the phase transform).
 * - Transform it back.
 *
 * Whitening turns the correlation into one sharp peak at the delay, even for music or speech.
 * Their plain correlation is broad and periodic, and room reverberation doesn't move the peak.
 *
 * Pure Kotlin (no Android types), so it is unit tested on the JVM.
 */
internal object GccPhat {

    /** Segment length in samples; each segment is zero-padded to twice that (linear correlation). */
    const val SEGMENT_LENGTH = 16_384

    private const val FFT_SIZE = 2 * SEGMENT_LENGTH

    /** Cross-spectrum bins weaker than this are left out of the phase transform. */
    private const val MIN_MAGNITUDE = 1e-12

    /**
     * Lag in samples of [reference]'s copy in [recorded] (positive: the copy is late), searched
     * in [minLag]..[maxLag]. Both signals start at the same time.
     *
     * Returns null when no lag stands out from the correlation over that range by [minPeakScore]
     * standard deviations: there is no copy, or both signals are silent.
     */
    fun estimateLag(
        reference: FloatArray,
        recorded: FloatArray,
        minLag: Int,
        maxLag: Int,
        minPeakScore: Double,
    ): Int? {
        require(minLag <= maxLag && maxOf(-minLag, maxLag) < SEGMENT_LENGTH) { "Lag range too wide" }
        val fft = Fft(FFT_SIZE)
        val window = FloatArray(SEGMENT_LENGTH) { i ->
            (0.5 - 0.5 * cos(2 * PI * i / (SEGMENT_LENGTH - 1))).toFloat()
        }
        val crossRe = DoubleArray(FFT_SIZE)
        val crossIm = DoubleArray(FFT_SIZE)
        val referenceRe = FloatArray(FFT_SIZE)
        val referenceIm = FloatArray(FFT_SIZE)
        val recordedRe = FloatArray(FFT_SIZE)
        val recordedIm = FloatArray(FFT_SIZE)

        val length = minOf(reference.size, recorded.size)
        var segments = 0
        var start = 0
        while (start + SEGMENT_LENGTH <= length) {
            referenceRe.fill(0f)
            referenceIm.fill(0f)
            recordedRe.fill(0f)
            recordedIm.fill(0f)
            for (i in 0 until SEGMENT_LENGTH) {
                referenceRe[i] = reference[start + i] * window[i]
                recordedRe[i] = recorded[start + i] * window[i]
            }
            fft.transform(referenceRe, referenceIm, inverse = false)
            fft.transform(recordedRe, recordedIm, inverse = false)
            for (k in 0 until FFT_SIZE) {
                // recorded x conj(reference): its inverse transform peaks at the copy's lag.
                crossRe[k] += (recordedRe[k] * referenceRe[k] + recordedIm[k] * referenceIm[k]).toDouble()
                crossIm[k] += (recordedIm[k] * referenceRe[k] - recordedRe[k] * referenceIm[k]).toDouble()
            }
            segments++
            start += SEGMENT_LENGTH / 2
        }
        if (segments == 0) return null

        // Phase transform.
        val re = FloatArray(FFT_SIZE)
        val im = FloatArray(FFT_SIZE)
        for (k in 0 until FFT_SIZE) {
            val magnitude = hypot(crossRe[k], crossIm[k])
            if (magnitude > MIN_MAGNITUDE) {
                re[k] = (crossRe[k] / magnitude).toFloat()
                im[k] = (crossIm[k] / magnitude).toFloat()
            }
        }
        fft.transform(re, im, inverse = true)

        // re[lag] is the correlation at that lag; negative lags wrap around to the end.
        var bestLag = minLag
        var best = Double.NEGATIVE_INFINITY
        var sum = 0.0
        var sumOfSquares = 0.0
        for (lag in minLag..maxLag) {
            val value = re[if (lag >= 0) lag else FFT_SIZE + lag].toDouble()
            if (value > best) {
                best = value
                bestLag = lag
            }
            sum += value
            sumOfSquares += value * value
        }
        val count = maxLag - minLag + 1
        val mean = sum / count
        val deviation = sqrt(maxOf(sumOfSquares / count - mean * mean, 0.0))
        if (deviation == 0.0) return null
        return bestLag.takeIf { (best - mean) / deviation >= minPeakScore }
    }
}

/** In-place iterative radix-2 FFT for one power-of-two [size]. */
internal class Fft(private val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "The size must be a power of two" }
    }

    private val cosTable = DoubleArray(size / 2) { cos(2 * PI * it / size) }
    private val sinTable = DoubleArray(size / 2) { sin(2 * PI * it / size) }

    /** Transforms [re] + i [im] in place; [inverse] transforms back, without the 1 / size scale. */
    fun transform(re: FloatArray, im: FloatArray, inverse: Boolean) {
        require(re.size == size && im.size == size) { "Expected $size samples" }
        // Bit-reversal permutation.
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val swapRe = re[i]
                re[i] = re[j]
                re[j] = swapRe
                val swapIm = im[i]
                im[i] = im[j]
                im[j] = swapIm
            }
        }
        // Butterflies: e^(-2 pi i k / n), or e^(+2 pi i k / n) when inverse.
        val sign = if (inverse) 1.0 else -1.0
        var half = 1
        while (half < size) {
            val tableStep = size / (2 * half)
            var start = 0
            while (start < size) {
                for (k in 0 until half) {
                    val wRe = cosTable[k * tableStep]
                    val wIm = sign * sinTable[k * tableStep]
                    val a = start + k
                    val b = a + half
                    val tRe = re[b] * wRe - im[b] * wIm
                    val tIm = re[b] * wIm + im[b] * wRe
                    re[b] = (re[a] - tRe).toFloat()
                    im[b] = (im[a] - tIm).toFloat()
                    re[a] = (re[a] + tRe).toFloat()
                    im[a] = (im[a] + tIm).toFloat()
                }
                start += 2 * half
            }
            half = half shl 1
        }
    }
}
