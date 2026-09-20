package io.github.arcioth.cadence.analyze

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class BeatTrackerTest {
    private fun clickTrack(sr: Int, seconds: Float, bpm: Float): FloatArray {
        val n = (sr * seconds).toInt()
        val data = FloatArray(n)
        val period = 60f / bpm
        var t = 0.2f
        while (t < seconds - 0.05f) {
            val start = (t * sr).toInt()
            val len = (sr * 0.012f).toInt()
            var i = 0
            while (i < len && start + i < n) {
                data[start + i] = (if (i % 2 == 0) 1f else -1f) * (1f - i / len.toFloat())
                i++
            }
            t += period
        }
        return data
    }

    @Test
    fun downsampleShrinks() {
        val src = FloatArray(44100)
        assertEquals(11025, BeatTracker.downsampleMono(src, 44100).size)
    }

    @Test
    fun estimatesNear120() {
        val sr = BeatTracker.TARGET_SR
        val mono = clickTrack(sr, 8f, 120f)
        val (flux, hop) = BeatTracker.spectralFlux(mono, sr)
        val bpm = BeatTracker.estimateBpm(flux, hop)
        assertTrue("bpm=$bpm", bpm in 110f..130f)
    }

    @Test
    fun gridContinuesThroughSilence() {
        val sr = BeatTracker.TARGET_SR
        val mono = clickTrack(sr, 4f, 120f)
        val (flux, hop) = BeatTracker.spectralFlux(mono, sr)
        val beats = BeatTracker.beatsFromTempo(flux, hop, 120f, 8f)
        assertTrue(beats.count { it.time > 5f } > 3)
    }

    @Test
    fun sineHasNoStrongGrid() {
        val sr = BeatTracker.TARGET_SR
        val n = sr * 2
        val s = FloatArray(n) { i -> sin(2.0 * Math.PI * 440.0 * i / sr).toFloat() * 0.2f }
        val (beats, _) = BeatTracker.analyze(s, 0f, sr)
        assertTrue(beats.size < 80)
    }
}
