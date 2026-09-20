package io.github.arcioth.cadence.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import io.github.arcioth.cadence.library.Track

class CadencePlayer(context: Context) {
    val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build().apply {
        pauseAtEndOfMediaItems = false
        repeatMode = Player.REPEAT_MODE_ALL
    }

    fun setQueue(tracks: List<Track>, start: Int) {
        exo.setMediaItems(tracks.map { MediaItem.fromUri(it.uri) }, start, 0L)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun toggle() {
        if (exo.isPlaying) exo.pause() else exo.play()
    }

    fun next() { exo.seekToNextMediaItem() }
    fun prev() { exo.seekToPreviousMediaItem() }

    fun index(): Int = exo.currentMediaItemIndex.coerceAtLeast(0)
    fun position(): Long = exo.currentPosition.coerceAtLeast(0L)
    fun duration(): Long = exo.duration.coerceAtLeast(0L)

    fun release() {
        exo.release()
    }
}
