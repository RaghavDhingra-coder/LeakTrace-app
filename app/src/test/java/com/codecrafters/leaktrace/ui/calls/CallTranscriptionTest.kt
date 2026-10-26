package com.codecrafters.leaktrace.ui.calls

import java.io.ByteArrayInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CallTranscriptionTest {
    @Test fun recordedPcmProducesRealDecoderText() = runTest {
        val pipeline = CallTranscriptionPipeline { pcm ->
            assertEquals(4, pcm.readBytes().size)
            "hello Ember"
        }
        assertEquals("hello Ember", pipeline.transcribe({ ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }, CallAudioKind.RecordedPcm))
    }

    @Test fun emptyAudioNeverInvokesDecoder() = runTest {
        var invoked = false
        val pipeline = CallTranscriptionPipeline { invoked = true; "invented" }
        try {
            pipeline.transcribe({ ByteArrayInputStream(byteArrayOf()) }, CallAudioKind.RecordedPcm)
            throw AssertionError("Empty audio was accepted")
        } catch (_: EmptyCallAudioException) { assertEquals(false, invoked) }
    }

    @Test fun wavImportRejectsUnsupportedFormatAndDecodesSupportedPcm() = runTest {
        val pipeline = CallTranscriptionPipeline { pcm ->
            assertEquals(listOf<Byte>(1, 2, 3, 4), pcm.readBytes().toList())
            "a spoken alias"
        }
        assertEquals("a spoken alias", pipeline.transcribe({ ByteArrayInputStream(wav(byteArrayOf(1, 2, 3, 4))) }, CallAudioKind.ImportedWav))
        try {
            pipeline.transcribe({ ByteArrayInputStream("ID3 compressed audio".toByteArray()) }, CallAudioKind.ImportedWav)
            throw AssertionError("Unsupported import was accepted")
        } catch (_: UnsupportedCallAudioException) { }
        try {
            pipeline.transcribe({ ByteArrayInputStream(wav(byteArrayOf(1, 2), channels = 2)) }, CallAudioKind.ImportedWav)
            throw AssertionError("Stereo import was accepted")
        } catch (_: UnsupportedCallAudioException) { }
    }

    @Test fun decoderFailureCanRetryWithoutInventingTranscript() = runTest {
        var attempts = 0
        val pipeline = CallTranscriptionPipeline { pcm ->
            pcm.readBytes()
            attempts++
            if (attempts == 1) error("decoder failed")
            "retry succeeds"
        }
        val open = { ByteArrayInputStream(byteArrayOf(1, 2)) }
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { pipeline.transcribe(open, CallAudioKind.RecordedPcm) } }
        assertEquals("retry succeeds", pipeline.transcribe(open, CallAudioKind.RecordedPcm))
        assertEquals(2, attempts)
    }

    @Test fun decoderSilenceIsNotReportedAsSpeech() = runTest {
        val pipeline = CallTranscriptionPipeline { "   " }
        try {
            pipeline.transcribe({ ByteArrayInputStream(byteArrayOf(1, 2)) }, CallAudioKind.RecordedPcm)
            throw AssertionError("Silent result was accepted")
        } catch (_: EmptyCallAudioException) { }
    }

    private fun wav(pcm: ByteArray, channels: Int = 1): ByteArray {
        val bytes = ArrayList<Byte>()
        fun text(value: String) { bytes.addAll(value.toByteArray().toList()) }
        fun u16(value: Int) { bytes.add(value.toByte()); bytes.add((value shr 8).toByte()) }
        fun u32(value: Int) { u16(value); u16(value shr 16) }
        text("RIFF"); u32(36 + pcm.size); text("WAVE")
        text("fmt "); u32(16); u16(1); u16(channels); u32(16_000); u32(16_000 * channels * 2); u16(channels * 2); u16(16)
        text("data"); u32(pcm.size); bytes.addAll(pcm.toList())
        return bytes.toByteArray()
    }
}
