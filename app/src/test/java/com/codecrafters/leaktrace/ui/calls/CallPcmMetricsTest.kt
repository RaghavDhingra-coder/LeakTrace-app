package com.codecrafters.leaktrace.ui.calls

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CallPcmMetricsTest {
    @Test fun pcm16ShortSamplesProduceCorrectLevelsAndLittleEndianBytes() {
        val samples = shortArrayOf(0, 1200, -1200, 0)
        val metrics = CallPcmMetrics().apply { add(samples, samples.size) }
        assertEquals(4, metrics.count)
        assertEquals(1200, metrics.peak)
        assertEquals(848, metrics.rms)
        assertArrayEquals(byteArrayOf(0, 0, -80, 4, 80, -5, 0, 0), pcm16Bytes(samples, samples.size))
        assertNull(CallAudioQuality.problem(true, 64_000, metrics.peak, metrics.rms, false, null))
    }

    @Test fun zeroOrSingleImpulseDoesNotPassAsCallAudio() {
        val silent = CallPcmMetrics().apply { add(ShortArray(16_000), 16_000) }
        assertNotNull(CallAudioQuality.problem(true, 32_000, silent.peak, silent.rms, false, null))
        val impulse = ShortArray(16_000).also { it[0] = 1000 }
        val metrics = CallPcmMetrics().apply { add(impulse, impulse.size) }
        assertEquals(1000, metrics.peak)
        assertNotNull(CallAudioQuality.problem(true, 32_000, metrics.peak, metrics.rms, false, null))
    }

    @Test fun frameworkSilencingIsReportedSeparatelyFromQuietSignal() {
        val message = CallAudioQuality.problem(true, 64_000, 1200, 848, true, null)
        assertNotNull(message)
        assertEquals(true, message!!.contains("Android"))
    }
}
