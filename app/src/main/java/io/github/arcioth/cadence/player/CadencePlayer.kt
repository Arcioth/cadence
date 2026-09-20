package io.github.arcioth.cadence.player

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.arcioth.cadence.library.Album
import io.github.arcioth.cadence.library.Track
import java.util.concurrent.atomic.AtomicLong

class CadencePlayer(private val context: Context) {
    var album: Album? = null
        private set
    var tracks: List<Track> = emptyList()
        private set
    val positionMs = AtomicLong(0L)

    val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        pauseAtEndOfMediaItems = false
        repeatMode = Player.REPEAT_MODE_ALL
        playWhenReady = true
    }

    fun setQueue(album: Album, start: Int = 0) {
        this.album = album
        this.tracks = album.tracks
        if (album.tracks.isEmpty()) return
        exo.setMediaItems(
            album.tracks.map { MediaItem.fromUri(it.uri) },
            start.coerceIn(0, album.tracks.lastIndex),
            0L,
        )
        exo.prepare()
        exo.playWhenReady = true
        startService()
    }

    fun toggle() {
        if (exo.isPlaying) exo.pause() else {
            exo.play()
            startService()
        }
    }

    fun next() { exo.seekToNextMediaItem() }
    fun prev() { exo.seekToPreviousMediaItem() }

    fun index(): Int = exo.currentMediaItemIndex.coerceAtLeast(0)
    fun position(): Long {
        val p = exo.currentPosition.coerceAtLeast(0L)
        positionMs.set(p)
        return p
    }
    fun duration(): Long {
        val d = exo.duration
        return if (d > 0) d else tracks.getOrNull(index())?.durationMs ?: 0L
    }

    private fun startService() {
        val i = Intent(context, PlaybackService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, i)
            else context.startService(i)
        } catch (_: Throwable) { }
    }
}
