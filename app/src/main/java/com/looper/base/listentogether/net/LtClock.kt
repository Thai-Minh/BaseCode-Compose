package com.looper.base.listentogether.net

class LtClock {

    private companion object {
        const val MAX_SAMPLES = 8
        const val SAMPLE_MAX_AGE_MS = 120_000L
    }

    private data class Sample(
        val roundTripMs: Long,
        val offsetMs: Long,
        val takenAt: Long,
    )

    private val samples = ArrayDeque<Sample>()

    @Volatile
    private var offsetMs: Long = 0L

    @Volatile
    var roundTripMs: Long = 0L
        private set

    @Volatile
    var isCalibrated: Boolean = false
        private set

    @Synchronized
    fun onPong(clientSentAt: Long, serverReceivedAt: Long, serverSentAt: Long, clientReceivedAt: Long = System.currentTimeMillis()) {
        val roundTrip = (clientReceivedAt - clientSentAt) - (serverSentAt - serverReceivedAt)
        if (roundTrip < 0) {
            return
        }
        val offset = ((serverReceivedAt - clientSentAt) + (serverSentAt - clientReceivedAt)) / 2
        val now = System.nanoTime() / 1_000_000L
        samples.addLast(element = Sample(roundTripMs = roundTrip, offsetMs = offset, takenAt = now))
        while (samples.size > MAX_SAMPLES || samples.first().takenAt < now - SAMPLE_MAX_AGE_MS) {
            samples.removeFirst()
        }
        val best = samples.minBy {
            it.roundTripMs
        }
        offsetMs = best.offsetMs
        roundTripMs = best.roundTripMs
        isCalibrated = true
    }

    fun serverNow(): Long = System.currentTimeMillis() + offsetMs

    @Synchronized
    fun reset() {
        samples.clear()
        offsetMs = 0L
        roundTripMs = 0L
        isCalibrated = false
    }
}
