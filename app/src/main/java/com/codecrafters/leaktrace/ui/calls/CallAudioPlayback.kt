package com.codecrafters.leaktrace.ui.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Plays PCM locally. Imported files are decoded only when they are supported WAV files. */
object CallAudioPlayback {
    private val playing = AtomicBoolean(false)
    private var track: AudioTrack? = null
    fun play(context: Context, owner: String, path: String): Boolean =
        playSource { CallAudioVault.open(context.applicationContext, owner, path) }

    fun playImport(context: Context, uri: Uri): Boolean = playSource {
        val source = context.contentResolver.openInputStream(uri) ?: throw UnsupportedCallAudioException()
        try { wavPcm(source) } catch (error: Exception) { source.close(); throw error }
    }

    private fun playSource(open: () -> InputStream): Boolean {
        if (!playing.compareAndSet(false, true)) return false
        Thread {
            var audio: AudioTrack? = null
            try {
                audio = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(8192).build()
                track = audio
                audio.play()
                open().use { source ->
                    val buffer = ByteArray(4096)
                    while (playing.get()) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        audio.write(buffer, 0, count)
                    }
                }
            } catch (_: Exception) { /* The transcription screen reports unsupported/corrupt audio. */ }
            finally { try { audio?.stop(); audio?.release() } catch (_: Exception) {} ; track = null; playing.set(false) }
        }.start()
        return true
    }
    fun stop() { playing.set(false); try { track?.pause() } catch (_: Exception) {} }
}
