package io.github.arcioth.cadence.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.text.format.DateFormat
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import io.github.arcioth.cadence.App
import io.github.arcioth.cadence.analyze.BeatTracker
import io.github.arcioth.cadence.library.Album
import io.github.arcioth.cadence.library.Library
import kotlinx.coroutines.isActive
import java.util.Date
import kotlin.math.exp
import kotlin.math.sin
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
    ) { granted = it.values.any { v -> v } }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(Bg).statusBarsPadding().padding(28.dp),
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
    var session by remember { mutableStateOf<Album?>(null) }
    var pulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(open) {
        if (open == null) {
            while (true) {
                kotlinx.coroutines.delay(500)
                pulse++
            }
        }
    }

    if (open == null) {
        Column(Modifier.fillMaxSize().background(Bg).statusBarsPadding().navigationBarsPadding()) {
            AlbumList(albums, Modifier.weight(1f)) { a ->
                val p = App.instance.playback
                if (p.album?.id != a.id) p.setQueue(a, 0)
                session = a
                open = a
            }
            if (session != null) {
                MiniBar(session!!, pulse) { open = session }
            }
        }
    } else {
        PlayerScreen(open!!) { open = null }
    }
}

@Composable
private fun MiniBar(album: Album, pulse: Int, onOpen: () -> Unit) {
    val p = App.instance.playback
    val i = p.index().coerceIn(0, album.tracks.lastIndex)
    pulse // recompose while backgrounded in list
    val t = album.tracks.getOrNull(i)
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF101826)).clickable { onOpen() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t?.title ?: album.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("playing · ${album.name}", color = Mute, fontSize = 12.sp, maxLines = 1)
        }
        Text(if (p.exo.isPlaying) "❚❚" else "▶", color = Pink, modifier = Modifier.clickable { p.toggle() }.padding(8.dp))
    }
}

@Composable
private fun AlbumList(albums: List<Album>, modifier: Modifier = Modifier, onOpen: (Album) -> Unit) {
    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
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
    val view = LocalView.current
    val player = App.instance.playback
    val look = App.instance.lookahead
    var index by remember { mutableIntStateOf(player.index()) }
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    val beats by look.beats.collectAsState()
    val bpm by look.bpm.collectAsState()
    val ahead by look.ahead.collectAsState()
    val sat by look.sat.collectAsState()
    var env by remember { mutableFloatStateOf(0f) }
    var punch by remember { mutableFloatStateOf(0f) }
    var fx by remember { mutableIntStateOf(0) }
    var lastHit by remember { mutableFloatStateOf(-1f) }
    var fullscreen by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var imageIndex by remember { mutableIntStateOf(0) }
    val posAtom = player.positionMs
    var notifyAsked by remember { mutableStateOf(false) }

    val notifyLaunch = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(16),
    ) { uris ->
        val take = uris.take(16)
        take.forEach { uri ->
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Throwable) { }
        }
        if (take.isNotEmpty()) {
            images = take
            imageIndex = 0
        }
    }

    LaunchedEffect(album.id) {
        if (player.album?.id != album.id) player.setQueue(album, 0)
        if (!look.isRunning()) {
            val tr = album.tracks.getOrNull(player.index()) ?: album.tracks.first()
            look.start(tr.uri, tr.durationMs, posAtom)
        }
        if (Build.VERSION.SDK_INT >= 33 && !notifyAsked) {
            notifyAsked = true
            val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!ok) notifyLaunch.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(album.id) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val i = player.index().coerceIn(0, album.tracks.lastIndex)
                index = i
                val tr = album.tracks[i]
                look.start(tr.uri, tr.durationMs, posAtom)
            }
        }
        player.exo.addListener(listener)
        onDispose { player.exo.removeListener(listener) }
    }

    DisposableEffect(fullscreen) {
        val window = (ctx as? Activity)?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, view) }
        if (fullscreen) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { }
            pos = player.position()
            posAtom.set(pos)
            dur = player.duration()
            playing = player.exo.isPlaying
            index = player.index().coerceIn(0, album.tracks.lastIndex)
            val t = pos / 1000f
            if (t < BeatTracker.PRIMER_SEC) {
                env = 0f
                punch = 0f
                continue
            }
            val last = beats.lastOrNull { it.time <= t }
            val dt = if (last == null) 99f else t - last.time
            env = if (last == null || dt < 0f || dt > 0.42f) 0f
            else (exp(-dt * 4.2f) * last.intensity)
            val satN = sat.norm(last?.energy ?: 0f)
            punch = env * (0.30f + 0.70f * satN)
            if (last != null && last.time != lastHit && dt < 0.05f) {
                lastHit = last.time
                fx = Random.nextInt(Fx.COUNT)
                if (images.size > 1) imageIndex = (imageIndex + 1) % images.size
            }
        }
    }

    val track = album.tracks.getOrNull(index) ?: return
    val chrome = if (fullscreen) Modifier else Modifier.statusBarsPadding().navigationBarsPadding()

    Box(Modifier.fillMaxSize().background(Bg).then(chrome)) {
        Column(
            Modifier.fillMaxSize().padding(if (fullscreen) 0.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!fullscreen) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("←  ${album.name}", color = Mute, modifier = Modifier.weight(1f).clickable { onBack() })
                    Text(
                        "Images",
                        color = Ice,
                        modifier = Modifier.clickable {
                            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }.padding(8.dp),
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            EditStage(
                title = track.title,
                image = images.getOrNull(imageIndex),
                fx = fx,
                punch = punch,
                env = env,
                fullscreen = fullscreen,
                onToggleFull = { fullscreen = !fullscreen },
                modifier = if (fullscreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth(0.94f).aspectRatio(1f),
            )
            if (!fullscreen) {
                Spacer(Modifier.height(8.dp))
                if (images.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        itemsIndexed(images) { i, uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                    .clickable { imageIndex = i },
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    listOfNotNull(
                        bpm.takeIf { it > 1f }?.let { "${it.toInt()} BPM" },
                        "buf ${ahead.toInt()}s",
                    ).joinToString("  ·  ").ifBlank { "mapping…" },
                    color = Ice,
                    fontSize = 13.sp,
                )
                Slider(
                    value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
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
        if (fullscreen) {
            FullscreenHud()
        }
    }
}

private object Fx {
    const val COUNT = 14
}

@Composable
private fun EditStage(
    title: String,
    image: Uri?,
    fx: Int,
    punch: Float,
    env: Float,
    fullscreen: Boolean,
    onToggleFull: () -> Unit,
    modifier: Modifier,
) {
    val e = punch
    val shake = sin(env * 42f) * e
    Box(
        modifier
            .pointerInput(fullscreen) {
                detectTapGestures(onDoubleTap = { onToggleFull() })
            }
            .graphicsLayer {
                translationY = when (fx) {
                    0 -> -e * 110f
                    1 -> e * 110f
                    10 -> shake * 90f
                    else -> 0f
                }
                translationX = when (fx) {
                    2 -> -e * 110f
                    3 -> e * 110f
                    11 -> shake * 90f
                    else -> 0f
                }
                val zIn = 1f + e * 0.30f
                val zOut = (1f - e * 0.22f).coerceAtLeast(0.7f)
                scaleX = when (fx) {
                    4 -> 1f + e * 0.30f
                    5 -> 1f - e * 0.12f
                    12 -> zIn
                    13 -> zOut
                    else -> 1f + e * 0.08f
                }
                scaleY = when (fx) {
                    4 -> 1f - e * 0.12f
                    5 -> 1f + e * 0.30f
                    12 -> zIn
                    13 -> zOut
                    else -> 1f + e * 0.08f
                }
                rotationZ = when (fx) {
                    6 -> e * 18f
                    7 -> -e * 18f
                    8 -> shake * 16f
                    9 -> -shake * 16f
                    else -> 0f
                }
                clip = !fullscreen
                shape = RoundedCornerShape(if (fullscreen) 0.dp else 28.dp)
            }
            .clip(RoundedCornerShape(if (fullscreen) 0.dp else 28.dp))
            .background(
                Brush.radialGradient(
                    listOf(Ice.copy(0.25f + e * 0.5f), Pink.copy(0.35f + e * 0.4f), Color(0xFF0B1220)),
                ),
            ),
        contentAlignment = Alignment.BottomStart,
    ) {
        if (image != null) {
            AsyncImage(
                model = image,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        if (!fullscreen || image == null) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(20.dp),
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun FullscreenHud() {
    val ctx = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var bat by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = DateFormat.format("HH:mm", Date()).toString()
            val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            bat = if (level >= 0) "${(level * 100 / scale)}%" else ""
            kotlinx.coroutines.delay(15_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp, end = 16.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Text("$clock  $bat", color = Color.White.copy(0.9f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
