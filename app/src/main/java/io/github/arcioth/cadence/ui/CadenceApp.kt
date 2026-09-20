package io.github.arcioth.cadence.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
private val Panel = Color(0xFF101826)

private fun audioPerm(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

private fun hasAudio(ctx: android.content.Context): Boolean =
    ContextCompat.checkSelfPermission(ctx, audioPerm()) == PackageManager.PERMISSION_GRANTED

@Composable
fun Ph(glyph: String, modifier: Modifier = Modifier, color: Color = Color.White, size: Int = 22) {
    Text(
        glyph,
        fontFamily = Phosphor,
        fontSize = size.sp,
        color = color,
        modifier = modifier,
        style = TextStyle(fontFamily = Phosphor),
    )
}

@Composable
fun CadenceRoot() {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAudio(ctx)) }
    val askAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted = it || hasAudio(ctx) }
    val askNotify = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) granted = hasAudio(ctx)
        }
        lifecycle.lifecycle.addObserver(obs)
        onDispose { lifecycle.lifecycle.removeObserver(obs) }
    }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(Bg).padding(top = 36.dp).padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Cadence", color = Ice, fontSize = 28.sp, fontFamily = Montserrat, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
            Spacer(Modifier.height(8.dp))
            Text("Music, on the beat.", color = Color.White, fontSize = 18.sp, fontFamily = Montserrat)
            Spacer(Modifier.height(16.dp))
            Text("Cadence needs access to music on this phone.", color = Mute, fontFamily = Montserrat)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { askAudio.launch(audioPerm()) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Bg),
            ) { Text("Allow music", fontFamily = Montserrat) }
            Spacer(Modifier.height(12.dp))
            Text(
                "Open settings",
                color = Ice,
                fontFamily = Montserrat,
                modifier = Modifier.clickable {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
            )
        }
        return
    }

    var albums by remember { mutableStateOf<List<Album>>(emptyList()) }
    LaunchedEffect(Unit) {
        albums = Library.load(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
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

    Box(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp).navigationBarsPadding()) {
            if (open == null) {
                AlbumList(albums, Modifier.weight(1f)) { a ->
                    val p = App.instance.playback
                    if (p.album?.id != a.id) p.setQueue(a, 0)
                    session = a
                    open = a
                }
                if (session != null) MiniBar(session!!, pulse) { open = session }
            } else {
                PlayerScreen(open!!, Modifier.weight(1f)) { open = null }
            }
        }
        StatusHud()
    }
}

@Composable
private fun StatusHud() {
    val ctx = LocalContext.current
    var clock by remember { mutableStateOf("") }
    var pct by remember { mutableIntStateOf(0) }
    var charging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (isActive) {
            clock = DateFormat.format("HH:mm", Date()).toString()
            val sticky = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = sticky?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
            val scale = sticky?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            pct = if (scale > 0) level * 100 / scale else 0
            charging = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING
            kotlinx.coroutines.delay(8_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, end = 14.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(clock, color = Color.White, fontSize = 12.sp, fontFamily = Montserrat, fontWeight = FontWeight.Normal)
        Spacer(Modifier.width(8.dp))
        Ph(batteryGlyph(pct, charging), color = Color.White, size = 16)
        Spacer(Modifier.width(4.dp))
        Text("$pct%", color = Color.White.copy(0.85f), fontSize = 11.sp, fontFamily = Montserrat)
    }
}

@Composable
private fun MiniBar(album: Album, pulse: Int, onOpen: () -> Unit) {
    val p = App.instance.playback
    val i = p.index().coerceIn(0, album.tracks.lastIndex)
    pulse
    val t = album.tracks.getOrNull(i)
    Row(
        Modifier.fillMaxWidth().background(Panel).clickable { onOpen() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t?.title ?: album.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Montserrat)
            Text(album.name, color = Mute, fontSize = 12.sp, maxLines = 1, fontFamily = Montserrat)
        }
        Ph(
            if (p.exo.isPlaying) Ph.Pause else Ph.Play,
            color = Pink,
            modifier = Modifier.clickable { p.toggle() }.padding(8.dp),
        )
    }
}

@Composable
private fun AlbumList(albums: List<Album>, modifier: Modifier = Modifier, onOpen: (Album) -> Unit) {
    var q by remember { mutableStateOf("") }
    var compact by remember { mutableStateOf(false) }
    val filtered = remember(albums, q) {
        val s = q.trim().lowercase()
        if (s.isEmpty()) albums
        else albums.filter { it.name.lowercase().contains(s) || it.artist.lowercase().contains(s) }
    }
    Column(modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Cadence",
            color = Ice,
            fontSize = 26.sp,
            fontFamily = Montserrat,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ph(Ph.Search, color = Mute, size = 18)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = q,
                onValueChange = { q = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontFamily = Montserrat, fontSize = 15.sp),
                cursorBrush = SolidColor(Ice),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (q.isEmpty()) Text("Search albums", color = Mute, fontFamily = Montserrat, fontSize = 15.sp)
                        inner()
                    }
                },
            )
            Ph(
                if (compact) Ph.List else Ph.Squares,
                color = Ice,
                size = 20,
                modifier = Modifier.clickable { compact = !compact }.padding(4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            Text("No albums.", color = Mute, fontFamily = Montserrat)
        } else if (compact) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtered, key = { it.id }) { a ->
                    Column(
                        Modifier.clip(RoundedCornerShape(12.dp)).background(Panel).clickable { onOpen(a) }.padding(8.dp),
                    ) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(Ice.copy(0.35f), Pink.copy(0.4f)))),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(a.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, fontFamily = Montserrat)
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { a ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel)
                            .clickable { onOpen(a) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(Ice.copy(0.4f), Pink.copy(0.5f)))),
                        )
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(a.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = Montserrat)
                            Text("${a.tracks.size} · ${a.artist}", color = Mute, fontSize = 12.sp, maxLines = 1, fontFamily = Montserrat)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerScreen(album: Album, modifier: Modifier = Modifier, onBack: () -> Unit) {
    val ctx = LocalContext.current
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
    var sinceBeat by remember { mutableFloatStateOf(9f) }
    var fx by remember { mutableIntStateOf(0) }
    var lastHit by remember { mutableFloatStateOf(-1f) }
    var fullscreen by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var imageIndex by remember { mutableIntStateOf(0) }
    val posAtom = player.positionMs

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(16),
    ) { uris ->
        val take = uris.take(16)
        take.forEach { uri ->
            try {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) { }
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
                sinceBeat = 9f
                continue
            }
            val last = beats.lastOrNull { it.time <= t }
            val dt = if (last == null) 99f else t - last.time
            sinceBeat = dt
            env = if (last == null || dt < 0f || dt > 0.38f) 0f
            else (exp(-dt * 5.2f) * last.intensity)
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
    Column(
        modifier.fillMaxSize().padding(horizontal = if (fullscreen) 0.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Ph(Ph.CaretLeft, color = Mute, modifier = Modifier.clickable { onBack() }.padding(6.dp))
                Text(
                    album.name,
                    color = Mute,
                    fontFamily = Montserrat,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Ph(
                    Ph.Images,
                    color = Ice,
                    modifier = Modifier.clickable {
                        pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }.padding(6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
        EditStage(
            title = track.title,
            image = images.getOrNull(imageIndex),
            fx = fx,
            punch = punch,
            sinceBeat = sinceBeat,
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
                            model = ImageRequest.Builder(ctx).data(uri).size(160).build(),
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable { imageIndex = i },
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
                fontFamily = Montserrat,
            )
            Slider(
                value = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                onValueChange = { player.exo.seekTo((it * dur).toLong()) },
                colors = SliderDefaults.colors(thumbColor = Ice, activeTrackColor = Ice),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Ph(Ph.SkipBack, modifier = Modifier.clickable { player.prev() }.padding(12.dp), size = 26)
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Pink).clickable { player.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    Ph(if (playing) Ph.Pause else Ph.Play, color = Bg, size = 28)
                }
                Ph(Ph.SkipFwd, modifier = Modifier.clickable { player.next() }.padding(12.dp), size = 26)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(album.tracks.size) { i ->
                    val tr = album.tracks[i]
                    Text(
                        tr.title,
                        color = if (i == index) Pink else Color.White,
                        fontFamily = Montserrat,
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
    sinceBeat: Float,
    fullscreen: Boolean,
    onToggleFull: () -> Unit,
    modifier: Modifier,
) {
    val e = punch
    val shake = sin(sinceBeat * 48f) * e
    val ctx = LocalContext.current
    Box(
        modifier
            .pointerInput(fullscreen) {
                detectTapGestures(onDoubleTap = { onToggleFull() })
            }
            .graphicsLayer {
                translationY = when (fx) {
                    0 -> -e * 110f
                    1 -> e * 110f
                    10 -> shake * 38f
                    else -> 0f
                }
                translationX = when (fx) {
                    2 -> -e * 110f
                    3 -> e * 110f
                    11 -> shake * 38f
                    else -> 0f
                }
                val zIn = 1f + e * 0.30f
                val zOut = (1f - e * 0.22f).coerceAtLeast(0.72f)
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
                    8 -> shake * 11f
                    9 -> -shake * 11f
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
                model = ImageRequest.Builder(ctx).data(image).size(1280).crossfade(true).build(),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(hsbMatrix(e)),
            )
        }
        if (!fullscreen || image == null) {
            Text(
                title,
                color = Color.White,
                fontFamily = Montserrat,
                fontSize = 18.sp,
                modifier = Modifier.padding(20.dp),
                maxLines = 2,
            )
        }
    }
}
