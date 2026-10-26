package com.looper.base.equalizer.model

import kotlin.math.ln

object EqBandMapper {

    fun mapToDevice(
        standardLevelsMb: List<Int>,
        deviceFreqsHz: List<Int>,
        standardFreqsHz: List<Int> = EqConstants.STANDARD_FREQS_HZ,
    ): List<Int> {
        if (deviceFreqsHz.isEmpty()) {
            return emptyList()
        }
        val levels = List(standardFreqsHz.size) {
            standardLevelsMb.getOrNull(it) ?: 0
        }

        if (deviceFreqsHz.size == standardFreqsHz.size) {
            return levels
        }

        return deviceFreqsHz.map { freq ->
            interpolate(
                freqHz = freq,
                freqs = standardFreqsHz,
                levels = levels
            )
        }
    }

    private fun interpolate(freqHz: Int, freqs: List<Int>, levels: List<Int>): Int {
        if (freqHz <= freqs.first()) {
            return levels.first()
        }
        if (freqHz >= freqs.last()) {
            return levels.last()
        }

        val upper = freqs.indexOfFirst {
            it >= freqHz
        }
        val lower = upper - 1
        if (freqs[upper] == freqHz) {
            return levels[upper]
        }

        val x0 = ln(freqs[lower].toDouble())
        val x1 = ln(freqs[upper].toDouble())
        val x = ln(freqHz.toDouble())
        val t = (x - x0) / (x1 - x0)

        return (levels[lower] + (levels[upper] - levels[lower]) * t).toInt()
    }
}
