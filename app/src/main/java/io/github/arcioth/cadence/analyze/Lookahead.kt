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

class Lookahead(private val context: Context, private val scope: CoroutineScope) {
    private val _beats = MutableStateFlow<List<Beat>>(emptyList())
    val beats: StateFlow<List<Beat>> = _beats
    private val _bpm = MutableStateFlow(0f)
    val bpm: StateFlow<Float> = _bpm
    private val _ahead = MutableStateFlow(0f)
    val ahead: StateFlow<Float> = _ahead

    private var job: Job? = null
    private var mappedUntilUs = 0L

    fun start(uri: Uri, durationMs: Long, positionMs: AtomicLong) {
        stop()
        mappedUntilUs = 0L
        _beats.value = emptyList()
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
                        val (more, bpm) = BeatTracker.analyze(mono, startSec)
                        if (bpm > 1f) _bpm.value = bpm
                        if (more.isNotEmpty()) {
                            _beats.value = (_beats.value + more).distinctBy { (it.time * 100).toInt() }
                        }
                    }
                } catch (_: Throwable) {
                    mappedUntilUs += 12_000_000L
                }
                delay(16)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        mappedUntilUs = 0L
    }
}
