package br.com.teasomagora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundComparisonTest {
    @Test
    fun quieterPointWorksInEitherOrder() {
        val quiet = SoundReading(-40.0, 2)
        val loud = SoundReading(-30.0, 2)
        assertEquals(Comparison.A_QUIETER, compare(quiet, loud))
        assertEquals(Comparison.B_QUIETER, compare(loud, quiet))
    }

    @Test
    fun nearbyLevelsAreInconclusive() {
        assertEquals(Comparison.INCONCLUSIVE, compare(SoundReading(-33.0, 2), SoundReading(-30.0, 2)))
    }

    @Test
    fun changedInputCannotBeCompared() {
        assertEquals(Comparison.DIFFERENT_INPUT, compare(SoundReading(-40.0, 2), SoundReading(-30.0, 3)))
    }

    @Test
    fun singleLoudWindowDoesNotDominate() {
        val usual = shortArrayOf(1000, -1000, 1000, -1000)
        val windows = SoundWindows(4).apply {
            add(usual, 4)
            add(shortArrayOf(10000, -10000, 10000, -10000), 4)
            add(usual, 4)
        }
        val reference = SoundWindows(4).apply { add(usual, 4) }
        assertEquals(reference.level()!!, windows.level()!!, 0.0001)
        assertFalse(windows.isClipped)
    }

    @Test
    fun mutedOrConstantInputDoesNotYieldALevel() {
        assertNull(SoundWindows(4).apply { add(ShortArray(8), 8) }.level())
        assertNull(SoundWindows(4).apply { add(ShortArray(8) { 123 }, 8) }.level())
    }

    @Test
    fun saturationIsDetected() {
        val windows = SoundWindows(4).apply {
            add(shortArrayOf(32767, -32768, 32767, -32768), 4)
        }
        assertTrue(windows.isClipped)
    }

    @Test
    fun windowsRemainConsistentAcrossPartialReads() {
        val samples = shortArrayOf(100, -100, 200, -200)
        val whole = SoundWindows(4).apply { add(samples, samples.size) }
        val partial = SoundWindows(4).apply {
            add(samples.copyOfRange(0, 3), 3)
            add(shortArrayOf(samples.last()), 1)
        }
        assertEquals(whole.level()!!, partial.level()!!, 0.0001)
    }
}
