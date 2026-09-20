package io.github.arcioth.cadence.analyze

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decode a short PCM window. Never holds the whole track.
 * Output: 11 kHz mono float.
 */
object WindowDecoder {
    fun decode(context: Context, uri: Uri, startUs: Long, durationUs: Long): Pair<FloatArray, Long>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val srcSr = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100
            val ch = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            } else 2
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            codec = softwareDecoder(mime) ?: MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val chunks = ArrayList<ShortArray>(32)
            var pcmCount = 0
            val info = MediaCodec.BufferInfo()
            var inEos = false
            var outEos = false
            val endUs = startUs + durationUs
            var loops = 0
            while (!outEos && loops++ < 8000) {
                if (!inEos) {
                    val ix = codec.dequeueInputBuffer(8_000)
                    if (ix >= 0) {
                        val buf = codec.getInputBuffer(ix)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0 || extractor.sampleTime > endUs + 200_000) {
                            codec.queueInputBuffer(ix, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inEos = true
                        } else {
                            codec.queueInputBuffer(ix, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val ox = codec.dequeueOutputBuffer(info, 8_000)
                if (ox >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outEos = true
                    if (info.size > 0 && info.presentationTimeUs + 50_000 >= startUs) {
                        val out = codec.getOutputBuffer(ox)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val shorts = ShortArray(info.size / 2)
                        out.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                        chunks.add(shorts)
                        pcmCount += shorts.size
                    }
                    codec.releaseOutputBuffer(ox, false)
                    if (info.presentationTimeUs > endUs) outEos = true
                }
            }
            if (pcmCount == 0) return null
            val pcm = ShortArray(pcmCount)
            var o = 0
            for (c in chunks) {
                c.copyInto(pcm, o)
                o += c.size
            }
            val mono = toMonoFloat(pcm, ch)
            val down = BeatTracker.downsampleMono(mono, srcSr)
            return down to startUs
        } catch (_: Throwable) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Throwable) {}
            try { codec?.release() } catch (_: Throwable) {}
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    private fun softwareDecoder(mime: String): MediaCodec? {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (info.isEncoder) continue
            if (info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) continue
            val n = info.name.lowercase()
            if ("google" in n || "c2.android" in n || n.contains(".sw.")) {
                return try { MediaCodec.createByCodecName(info.name) } catch (_: Throwable) { null }
            }
        }
        return null
    }

    private fun toMonoFloat(pcm: ShortArray, ch: Int): FloatArray {
        if (ch <= 1) {
            return FloatArray(pcm.size) { pcm[it] / 32768f }
        }
        val frames = pcm.size / ch
        val out = FloatArray(frames)
        var i = 0
        while (i < frames) {
            var s = 0f
            var c = 0
            while (c < ch) {
                s += pcm[i * ch + c] / 32768f
                c++
            }
            out[i] = s / ch
            i++
        }
        return out
    }
}
