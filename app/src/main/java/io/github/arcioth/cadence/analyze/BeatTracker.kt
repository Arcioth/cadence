package io.github.arcioth.cadence.analyze

import kotlin.math.abs
import kotlin.math.roundToInt

data class Beat(
    val time: Float,
    val intensity: Float,
    val energy: Float = intensity,
)

data class Analysis(
    val beats: List<Beat>,
    val bpm: Float,
    val localBpm: Float,
    val energy: Float,
)

object BeatTracker {
    const val TARGET_SR = 11025
    const val PRIMER_SEC = 0.08f
    private const val N = 1024
    private const val HOP = 512
    private val hann = FloatArray(N) { i ->
        (0.5 * (1.0 - kotlin.math.cos(2.0 * Math.PI * i / (N - 1)))).toFloat()
    }

    fun downsampleMono(ch0: FloatArray, srcSr: Int, ch1: FloatArray? = null): FloatArray {
        val ratio = srcSr.toFloat() / TARGET_SR
        val n = (ch0.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        var i = 0
        while (i < n) {
            val i0 = (i * ratio).toInt().coerceIn(0, ch0.lastIndex)
            var s = ch0[i0]
            if (ch1 != null) s = (s + ch1[i0.coerceIn(0, ch1.lastIndex)]) * 0.5f
            out[i] = s
            i++
        }
        return out
    }

    fun analyze(mono: FloatArray, startTime: Float, sr: Int = TARGET_SR, lockedBpm: Float = 0f): Analysis {
        val (flux, hopTime) = spectralFlux(mono, sr)
        if (flux.isEmpty()) return Analysis(emptyList(), lockedBpm, 0f, 0f)
        val energy = flux.average().toFloat()
        val local = estimateBpm(flux, hopTime)
        val bpm = if (lockedBpm > 1f) lockedBpm else local
        val duration = mono.size.toFloat() / sr
        var beats = beatsFromTempo(flux, hopTime, bpm, duration)
        beats = snapBeats(beats, bpm)
        if (startTime < 0.2f) beats = beats.filter { it.time >= PRIMER_SEC }
        beats = beats.map { it.copy(time = it.time + startTime) }
        return Analysis(beats, bpm, local, energy)
    }

    /**
     * Broadband flux, but sub-bass is down-weighted so hats/snare/mids
     * can win the onset instead of a long bass bloom.
     */
    internal fun spectralFlux(mono: FloatArray, sr: Int): Pair<FloatArray, Float> {
        val hopTime = HOP.toFloat() / sr
        val frames = ((mono.size - N) / HOP).coerceAtLeast(0)
        val flux = FloatArray(frames)
        val prev = FloatArray(N / 2)
        val re = FloatArray(N)
        val im = FloatArray(N)
        val ny = N / 2
        var fi = 0
        var i = 0
        while (i + N < mono.size && fi < frames) {
            re.fill(0f)
            im.fill(0f)
            var j = 0
            while (j < N) {
                re[j] = mono[i + j] * hann[j]
                j++
            }
            fftRadix2(re, im)
            var f = 0f
            var k = 1
            while (k < ny) {
                val mag = kotlin.math.hypot(re[k], im[k])
                val d = mag - prev[k]
                if (d > 0f) f += d * bandWeight(k, ny)
                prev[k] = mag
                k++
            }
            flux[fi++] = f
            i += HOP
        }
        return flux to hopTime
    }

    /** bin 0 ~ 0 Hz, ny ~ Nyquist (5512 Hz at 11 kHz). */
    private fun bandWeight(k: Int, ny: Int): Float {
        val hz = k * (TARGET_SR.toFloat() / (2 * ny))
        return when {
            hz < 80f -> 0.15f
            hz < 180f -> 0.45f
            hz < 2000f -> 1.35f
            hz < 6000f -> 1.15f
            else -> 0.7f
        }
    }

    internal fun estimateBpm(flux: FloatArray, hopTime: Float, minBpm: Float = 70f, maxBpm: Float = 180f): Float {
        val minLag = (60f / maxBpm / hopTime).toInt().coerceAtLeast(2)
        val maxLag = (60f / minBpm / hopTime).toInt().coerceAtMost(flux.size - 1)
        var best = -1f
        var bestLag = minLag
        var lag = minLag
        while (lag <= maxLag) {
            var s = 0f
            var i = 0
            while (i + lag < flux.size) {
                s += flux[i] * flux[i + lag]
                i++
            }
            val bpm = 60f / (lag * hopTime)
            val prefer = 1f - abs(bpm - 120f) / 180f
            val score = s * (0.75f + 0.25f * prefer)
            if (score > best) {
                best = score
                bestLag = lag
            }
            lag++
        }
        return ((60f / (bestLag * hopTime)) * 10f).toInt() / 10f
    }

    internal fun beatsFromTempo(flux: FloatArray, hopTime: Float, bpm: Float, duration: Float): List<Beat> {
        if (bpm <= 1f || flux.isEmpty()) return emptyList()
        val period = 60f / bpm
        val pf = (period / hopTime).toInt().coerceAtLeast(2)
        var bestPhase = 0
        var best = -1f
        var p = 0
        while (p < pf) {
            var s = 0f
            var i = p
            while (i < flux.size) {
                s += flux[i]
                i += pf
            }
            if (s > best) {
                best = s
                bestPhase = p
            }
            p++
        }
        val mean = flux.average().toFloat().coerceAtLeast(1e-6f)
        val out = ArrayList<Beat>()
        var t = bestPhase * hopTime
        while (t < duration - 0.02f && out.size < 8000) {
            val fi = (t / hopTime).toInt().coerceIn(0, flux.lastIndex)
            val onset = flux[fi]
            val intensity = (onset / (mean * 2f)).coerceIn(0.2f, 1f)
            out.add(Beat(t, intensity, onset))
            t += period
        }
        return out
    }

    /** Close pair: keep the hit closer to the locked BPM grid. */
    internal fun snapBeats(beats: List<Beat>, bpm: Float): List<Beat> {
        if (beats.isEmpty() || bpm <= 1f) return beats
        val period = 60f / bpm
        val minGap = period * 0.45f
        val sorted = beats.sortedBy { it.time }
        val phase = sorted.first().time
        val out = ArrayList<Beat>(sorted.size)
        for (b in sorted) {
            val last = out.lastOrNull()
            if (last != null && b.time - last.time < minGap) {
                val err = { x: Beat ->
                    val k = ((x.time - phase) / period).roundToInt()
                    abs(x.time - (phase + k * period))
                }
                if (err(b) < err(last)) out[out.lastIndex] = b
            } else {
                out.add(b)
            }
        }
        return out
    }

    private fun fftRadix2(re: FloatArray, im: FloatArray) {
        val n = re.size
        var i = 1
        var j = 0
        while (i < n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
            i++
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * Math.PI / len
            val wr0 = kotlin.math.cos(ang).toFloat()
            val wi0 = kotlin.math.sin(ang).toFloat()
            i = 0
            while (i < n) {
                var wr = 1f
                var wi = 0f
                val half = len shr 1
                var jj = 0
                while (jj < half) {
                    val ur = re[i + jj]
                    val ui = im[i + jj]
                    val vr = re[i + jj + half] * wr - im[i + jj + half] * wi
                    val vi = re[i + jj + half] * wi + im[i + jj + half] * wr
                    re[i + jj] = ur + vr
                    im[i + jj] = ui + vi
                    re[i + jj + half] = ur - vr
                    im[i + jj + half] = ui - vi
                    val nwr = wr * wr0 - wi * wi0
                    wi = wr * wi0 + wi * wr0
                    wr = nwr
                    jj++
                }
                i += len
            }
            len = len shl 1
        }
    }
}
