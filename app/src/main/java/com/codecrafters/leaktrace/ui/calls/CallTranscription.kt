package com.codecrafters.leaktrace.ui.calls

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.ZipInputStream

/** Supported input is 16 kHz, mono, signed 16-bit PCM; imports use a PCM WAV container. */
enum class CallAudioKind { RecordedPcm, ImportedWav }
class EmptyCallAudioException : Exception("The audio is empty or contains no recognizable speech.")
class UnsupportedCallAudioException : Exception("Use a 16 kHz mono 16-bit PCM WAV file for import.")

fun interface CallSpeechDecoder { suspend fun decode(pcm: InputStream): String }

class CallTranscriptionPipeline(private val decoder: CallSpeechDecoder) {
    suspend fun transcribe(open: () -> InputStream, kind: CallAudioKind): String {
        open().use { original ->
            val pcm = if (kind == CallAudioKind.ImportedWav) wavPcm(original) else original
            PushbackInputStream(pcm, 1).use { checked ->
                val first = checked.read()
                if (first < 0) throw EmptyCallAudioException()
                checked.unread(first)
                return decoder.decode(checked).trim().takeIf { it.isNotEmpty() }
                    ?: throw EmptyCallAudioException()
            }
        }
    }
}

private fun InputStream.exact(size: Int): ByteArray = ByteArray(size).also {
    var offset = 0
    while (offset < size) {
        val count = read(it, offset, size - offset)
        if (count < 0) throw UnsupportedCallAudioException()
        offset += count
    }
}
private fun ByteArray.u16(at: Int) = (this[at].toInt() and 255) or ((this[at + 1].toInt() and 255) shl 8)
private fun ByteArray.u32(at: Int): Long = (u16(at).toLong() or (u16(at + 2).toLong() shl 16))
private fun InputStream.discard(size: Long) {
    var left = size
    val buffer = ByteArray(4096)
    while (left > 0) {
        val count = read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
        if (count < 0) throw UnsupportedCallAudioException()
        left -= count
    }
}
private class BoundedInput(private val source: InputStream, private var left: Long) : InputStream() {
    override fun read(): Int = if (left <= 0) -1 else source.read().also { if (it >= 0) left-- }
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        if (left <= 0) -1 else source.read(bytes, offset, minOf(length.toLong(), left).toInt())
            .also { if (it > 0) left -= it }
}
internal fun wavPcm(source: InputStream): InputStream {
    val header = source.exact(12)
    if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" ||
        String(header, 8, 4, Charsets.US_ASCII) != "WAVE") throw UnsupportedCallAudioException()
    var validFormat = false
    repeat(100) {
        val chunk = source.exact(8)
        val name = String(chunk, 0, 4, Charsets.US_ASCII)
        val size = chunk.u32(4)
        if (size > 32_000_000) throw UnsupportedCallAudioException()
        when (name) {
            "fmt " -> {
                if (size < 16) throw UnsupportedCallAudioException()
                val format = source.exact(16)
                validFormat = format.u16(0) == 1 && format.u16(2) == 1 &&
                    format.u32(4) == 16_000L && format.u32(8) == 32_000L &&
                    format.u16(12) == 2 && format.u16(14) == 16
                source.discard(size - 16)
            }
            "data" -> {
                if (!validFormat || size % 2 != 0L) throw UnsupportedCallAudioException()
                return BoundedInput(source, size)
            }
            else -> source.discard(size)
        }
        if (size % 2 == 1L) source.discard(1)
    }
    throw UnsupportedCallAudioException()
}

/** Bundled Apache-licensed Vosk model. Decoding and model extraction run on Dispatchers.IO. */
class VoskCallTranscriber(private val context: Context) : CallSpeechDecoder {
    override suspend fun decode(pcm: InputStream): String {
        val recognizer = Recognizer(model(context.applicationContext), 16_000.0f)
        val parts = mutableListOf<String>()
        try {
            val buffer = ByteArray(4096)
            var total = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = pcm.read(buffer)
                if (count < 0) break
                total += count
                if (total > 30_000_000) throw UnsupportedCallAudioException()
                if (recognizer.acceptWaveForm(buffer, count))
                    JSONObject(recognizer.result).optString("text").takeIf(String::isNotBlank)?.let(parts::add)
            }
            JSONObject(recognizer.finalResult).optString("text").takeIf(String::isNotBlank)?.let(parts::add)
            return parts.joinToString(" ")
        } finally { recognizer.close() }
    }

    companion object {
        @Volatile private var cachedModel: Model? = null
        @Synchronized private fun model(context: Context): Model {
            cachedModel?.let { return it }
            val folder = File(context.filesDir, "vosk_model_en_us_v1")
            if (!File(folder, "conf/model.conf").isFile) {
                val staging = File(context.filesDir, "vosk_model_en_us_v1_partial")
                staging.deleteRecursively()
                staging.mkdirs()
                try {
                    var total = 0L
                    context.assets.open("vosk-model-small-en-us-0.15.zip").use { asset ->
                        ZipInputStream(asset).use { zip ->
                            while (true) {
                                val entry = zip.nextEntry ?: break
                                val relative = entry.name.removePrefix("vosk-model-small-en-us-0.15/")
                                if (relative == entry.name || relative.isBlank()) continue
                                val destination = File(staging, relative).canonicalFile
                                require(destination.path.startsWith(staging.canonicalPath + File.separator))
                                if (entry.isDirectory) destination.mkdirs() else {
                                    destination.parentFile?.mkdirs()
                                    destination.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        while (true) {
                                            val count = zip.read(buffer)
                                            if (count < 0) break
                                            total += count
                                            check(total <= 100_000_000) { "Speech model exceeds size limit" }
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                }
                                zip.closeEntry()
                            }
                        }
                    }
                    check(File(staging, "conf/model.conf").isFile) { "Speech model is incomplete" }
                    folder.deleteRecursively()
                    check(staging.renameTo(folder)) { "Speech model could not be installed" }
                } catch (error: Exception) { staging.deleteRecursively(); throw error }
            }
            return Model(folder.absolutePath).also { cachedModel = it }
        }
    }
}

suspend fun transcribeRecording(context: Context, owner: String, path: String): String =
    CallTranscriptionPipeline(VoskCallTranscriber(context)).transcribe(
        { CallAudioVault.open(context, owner, path) }, CallAudioKind.RecordedPcm)

suspend fun transcribeImport(context: Context, uri: Uri): String =
    CallTranscriptionPipeline(VoskCallTranscriber(context)).transcribe(
        { context.contentResolver.openInputStream(uri) ?: throw UnsupportedCallAudioException() }, CallAudioKind.ImportedWav)
