package br.com.teasomagora

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

internal const val MEASUREMENT_SECONDS = 10

internal data class SoundReading(val level: Double, val inputId: Int?)

internal enum class Comparison { A_QUIETER, B_QUIETER, INCONCLUSIVE, DIFFERENT_INPUT }

internal fun compare(a: SoundReading, b: SoundReading): Comparison {
    if (a.inputId != b.inputId) return Comparison.DIFFERENT_INPUT

    // Provisional margin for repeatability testing, expressed in digital signal units.
    val difference = a.level - b.level
    return when {
        abs(difference) <= 3.0 -> Comparison.INCONCLUSIVE
        difference < 0 -> Comparison.A_QUIETER
        else -> Comparison.B_QUIETER
    }
}

internal class SoundWindows(private val samplesPerWindow: Int) {
    private val windowPowers = mutableListOf<Double>()
    private var windowSamples = 0
    private var sum = 0.0
    private var sumOfSquares = 0.0
    var samples = 0
        private set
    private var saturatedSamples = 0

    val isClipped: Boolean
        get() = samples > 0 && saturatedSamples.toDouble() / samples > 0.01

    fun add(buffer: ShortArray, count: Int) {
        repeat(count) { index ->
            val sample = buffer[index].toDouble()
            if (abs(sample) >= 32760) saturatedSamples++
            sum += sample
            sumOfSquares += sample * sample
            samples++
            windowSamples++

            if (windowSamples == samplesPerWindow) {
                val mean = sum / windowSamples
                // Remove DC offset so a steady offset does not count as sound.
                val power = max(0.0, sumOfSquares / windowSamples - mean * mean)
                windowPowers.add(power)
                windowSamples = 0
                sum = 0.0
                sumOfSquares = 0.0
            }
        }
    }

    fun level(): Double? {
        if (windowPowers.isEmpty() || windowPowers.none { it > 0.0 }) return null
        val sorted = windowPowers.sorted()
        val middle = sorted.size / 2
        val median = (sorted[middle] + sorted[(sorted.size - 1) / 2]) / 2
        return 10 * log10(max(median, 1.0) / (32768.0 * 32768.0))
    }
}
