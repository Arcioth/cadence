package io.github.arcioth.cadence.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class Track(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val uri: Uri,
    val albumId: Long,
    val art: Uri?,
)

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val art: Uri?,
    val tracks: List<Track>,
)

object Library {
    fun load(context: Context): List<Album> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.IS_MUSIC,
        )
        val q = context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC}!=0",
            null,
            "${MediaStore.Audio.Media.ALBUM}, ${MediaStore.Audio.Media.TRACK}, ${MediaStore.Audio.Media.TITLE}",
        ) ?: return emptyList()
        q.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albums = LinkedHashMap<Long, MutableList<Track>>()
            val names = HashMap<Long, Pair<String, String>>()
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val albumId = c.getLong(iAlbumId)
                val art = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId,
                )
                val t = Track(
                    id = id,
                    title = c.getString(iTitle) ?: "Track",
                    durationMs = c.getLong(iDur),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    albumId = albumId,
                    art = art,
                )
                albums.getOrPut(albumId) { ArrayList() }.add(t)
                names[albumId] = (c.getString(iAlbum) ?: "Album") to (c.getString(iArtist) ?: "")
            }
            return albums.map { (id, ts) ->
                val (name, artist) = names[id] ?: ("Album" to "")
                Album(id, name, artist, ts.firstOrNull()?.art, ts)
            }
        }
    }
}
