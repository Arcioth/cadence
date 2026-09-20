package io.github.arcioth.cadence.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.arcioth.cadence.analyze.Lookahead
import io.github.arcioth.cadence.library.Album
import io.github.arcioth.cadence.library.Library
import io.github.arcioth.cadence.player.CadencePlayer
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.exp
import kotlin.random.Random

private val Bg = Color(0xFF05080C)
private val Ice = Color(0xFF7EC8E3)
private val Pink = Color(0xFFEC4899)
private val Mute = Color(0xFF8AA0B2)

private val audioPerms = if (Build.VERSION.SDK_INT >= 33) {
    arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_IMAGES)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
fun CadenceRoot() {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(audioPerms.all {
            ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val launch = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted = it.values.all { v -> v } }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(Bg).padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Cadence", color = Ice, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text("Music, on the beat.", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Text("Allow audio so albums on this phone can play.", color = Mute)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { launch.launch(audioPerms) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Bg),
            ) { Text("Allow music") }
        }
        return
    }

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    LaunchedEffect(Unit) { albums = Library.load(ctx) }
    var open by remember { mutableStateOf<Album?>(null) }
    if (open == null) {
        AlbumList(albums) { open = it }
    } else {
        PlayerScreen(open!!) { open = null }
    }
}

@Composable
private fun AlbumList(albums: List<Album>, onOpen: (Album) -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text("CADENCE", color = Ice, fontSize = 12.sp, letterSpacing = 4.sp)
        Spacer(Modifier.height(6.dp))
        Text("Albums", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (albums.isEmpty()) {
            Text("No music in the library yet.", color = Mute)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(albums, key = { it.id }) { a ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF101826))
                            .clickable { onOpen(a) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Ice.copy(0.4f), Pink.copy(0.5f)))),
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(a.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text("${a.tracks.size} tracks · ${a.artist}", color = Mute, fontSize = 13.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(album: Album, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember { CadencePlayer(ctx) }
    val look = remember { Lookahead(ctx, scope) }
    var index by remember { mutableIntStateOf(0) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    val beats by look.beats.collectAsState()
    val bpm by look.bpm.collectAsState()
    val ahead by look.ahead.collectAsState()
    var env by remember { mutableFloatStateOf(0f) }
    var fx by remember { mutableIntStateOf(0) }
    var lastHit by remember { mutableFloatStateOf(-1f) }
    val posAtom = remember { AtomicLong(0L) }

    DisposableEffect(album) {
        player.setQueue(album.tracks, 0)
        val t0 = album.tracks[0]
        look.start(t0.uri, t0.durationMs, posAtom)
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val i = player.index().coerceIn(0, album.tracks.lastIndex)
                index = i
                val tr = album.tracks[i]
                look.start(tr.uri, tr.durationMs, posAtom)
            }
        }
        player.exo.addListener(listener)
        onDispose {
            player.exo.removeListener(listener)
            look.stop()
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            withFrameNanos { }
            pos = player.position()
            posAtom.set(pos)
            dur = player.duration()
            playing = player.exo.isPlaying
            index = player.index().coerceIn(0, album.tracks.lastIndex)
            val t = pos / 1000f
            val last = beats.lastOrNull { it.time <= t }
            env = if (last == null) 0f else {
                val dt = t - last.time
                if (dt < 0f || dt > 0.55f) 0f
                else (exp(-dt * 2.6f) * last.intensity)
            }
            if (last != null && last.time != lastHit && t - last.time < 0.06f) {
                lastHit = last.time
                fx = Random.nextInt(8)
            }
        }
    }

    val track = album.tracks.getOrNull(index) ?: return
    Column(
        Modifier.fillMaxSize().background(Bg).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("←  ${album.name}", color = Mute, modifier = Modifier.fillMaxWidth().clickable { onBack() })
        Spacer(Modifier.height(18.dp))
        Box(
            Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(1f)
                .graphicsLayer {
                    val e = env
                    translationY = when (fx % 8) {
                        0 -> -e * 36f
                        1 -> e * 36f
                        2 -> 0f
                        3 -> 0f
                        else -> 0f
                    }
                    translationX = when (fx % 8) {
                        2 -> -e * 36f
                        3 -> e * 36f
                        else -> 0f
                    }
                    scaleX = when (fx % 8) {
                        4 -> 1f + e * 0.16f
                        5 -> 1f - e * 0.08f
                        else -> 1f + e * 0.04f
                    }
                    scaleY = when (fx % 8) {
                        4 -> 1f - e * 0.08f
                        5 -> 1f + e * 0.16f
                        else -> 1f + e * 0.04f
                    }
                    rotationZ = when (fx % 8) {
                        6 -> e * 9f
                        7 -> -e * 9f
                        else -> 0f
                    }
                    clip = true
                    shape = RoundedCornerShape(28.dp)
                }
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.radialGradient(listOf(Ice.copy(0.35f + env * 0.4f), Pink.copy(0.45f), Color(0xFF0B1220))),
                ),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                track.title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(20.dp),
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            listOfNotNull(
                bpm.takeIf { it > 1f }?.let { "${it.toInt()} BPM" },
                "buf ${ahead.toInt()}s",
            ).joinToString("  ·  ").ifBlank { "mapping…" },
            color = Ice,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Slider(
            value = if (dur > 0) pos.toFloat() / dur else 0f,
            onValueChange = { player.exo.seekTo((it * dur).toLong()) },
            colors = SliderDefaults.colors(thumbColor = Ice, activeTrackColor = Ice),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Text("⟨⟨", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { player.prev() }.padding(12.dp))
            Box(
                Modifier.size(68.dp).clip(CircleShape).background(Pink).clickable { player.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (playing) "❚❚" else "▶", color = Bg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Text("⟩⟩", color = Color.White, fontSize = 22.sp, modifier = Modifier.clickable { player.next() }.padding(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(album.tracks.size) { i ->
                val tr = album.tracks[i]
                Text(
                    tr.title,
                    color = if (i == index) Pink else Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            player.exo.seekTo(i, 0)
                            look.start(tr.uri, tr.durationMs, posAtom)
                        }
                        .padding(vertical = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

}
