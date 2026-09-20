package io.github.arcioth.cadence.analyze

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

data class Saturation(val min: Float = 1f, val max: Float = 1f) {
    fun norm(energy: Float): Float {
        val span = (max - min).coerceAtLeast(1e-4f)
        return ((energy - min) / span).coerceIn(0f, 1f)
    }
}

class Lookahead(private val context: Context, private val scope: CoroutineScope) {
    private val _beats = MutableStateFlow<List<Beat>>(emptyList())
    val beats: StateFlow<List<Beat>> = _beats
    private val _bpm = MutableStateFlow(0f)
    val bpm: StateFlow<Float> = _bpm
    private val _ahead = MutableStateFlow(0f)
    val ahead: StateFlow<Float> = _ahead
    private val _sat = MutableStateFlow(Saturation())
    val sat: StateFlow<Saturation> = _sat

    private var job: Job? = null
    private var mappedUntilUs = 0L
    private var lockedBpm = 0f
    private var satMin = Float.MAX_VALUE
    private var satMax = 1e-6f

    fun start(uri: Uri, durationMs: Long, positionMs: AtomicLong) {
        stop()
        mappedUntilUs = 0L
        lockedBpm = 0f
        satMin = Float.MAX_VALUE
        satMax = 1e-6f
        _beats.value = emptyList()
        _bpm.value = 0f
        _sat.value = Saturation()
        job = scope.launch(Dispatchers.Default) {
            val durationUs = (durationMs * 1000L).coerceAtLeast(1_000_000L)
            while (isActive) {
                try {
                    val posUs = positionMs.get().coerceAtLeast(0L) * 1000L
                    val aheadUs = mappedUntilUs - posUs
                    _ahead.value = (aheadUs / 1_000_000f).coerceAtLeast(0f)
                    val keepFrom = (posUs / 1_000_000f) - 20f
                    _beats.value = _beats.value.filter { it.time >= keepFrom }
                    if (mappedUntilUs >= durationUs - 200_000L) {
                        delay(400)
                        continue
                    }
                    if (aheadUs >= 40_000_000L) {
                        delay(200)
                        continue
                    }
                    val windowUs = 12_000_000L
                    val requestStart = mappedUntilUs
                    val decoded = WindowDecoder.decode(context, uri, requestStart, windowUs)
                    mappedUntilUs = requestStart + windowUs
                    if (decoded != null) {
                        val (mono, startUs) = decoded
                        val startSec = startUs / 1_000_000f
                        val a = BeatTracker.analyze(mono, startSec, lockedBpm = lockedBpm)
                        lockBpm(a.localBpm)
                        if (a.energy > 0f) {
                            if (a.energy < satMin) satMin = a.energy
                            if (a.energy > satMax) satMax = a.energy
                            _sat.value = Saturation(satMin, satMax)
                        }
                        if (a.beats.isNotEmpty()) {
                            val merged = BeatTracker.snapBeats(_beats.value + a.beats, lockedBpm.takeIf { it > 1f } ?: a.bpm)
                            _beats.value = merged
                        }
                    }
                } catch (_: Throwable) {
                    mappedUntilUs += 12_000_000L
                }
                delay(16)
            }
        }
    }

    private fun lockBpm(local: Float) {
        if (local <= 1f) return
        if (lockedBpm < 1f) {
            lockedBpm = local
            _bpm.value = local
            return
        }
        val drift = abs(local - lockedBpm) / lockedBpm
        if (drift < 0.06f) {
            lockedBpm = lockedBpm * 0.92f + local * 0.08f
            _bpm.value = lockedBpm
        }
    }

    fun isRunning(): Boolean = job?.isActive == true

    fun stop() {
        job?.cancel()
        job = null
        mappedUntilUs = 0L
    }
}
