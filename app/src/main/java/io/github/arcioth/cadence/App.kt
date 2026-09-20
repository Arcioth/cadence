package io.github.arcioth.cadence

import android.app.Application
import io.github.arcioth.cadence.analyze.Lookahead
import io.github.arcioth.cadence.player.CadencePlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    lateinit var playback: CadencePlayer
        private set
    lateinit var lookahead: Lookahead
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        playback = CadencePlayer(this)
        lookahead = Lookahead(this, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }

    companion object {
        lateinit var instance: App
            private set
    }
}
