package io.github.arcioth.cadence.player

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import io.github.arcioth.cadence.App

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, App.instance.playback.exo).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
