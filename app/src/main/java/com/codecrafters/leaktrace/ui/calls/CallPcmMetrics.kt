package com.codecrafters.leaktrace.ui.calls

import kotlin.math.abs
import kotlin.math.sqrt

/** Content-free signal levels for signed PCM16 samples. */
class CallPcmMetrics {
    var count: Int = 0
        private set
    var peak: Int = 0
        private set
    private var energy: Long = 0
    val rms: Int get() = if (count == 0) 0 else sqrt(energy.toDouble() / count).toInt()

    fun add(samples: ShortArray, length: Int) {
        require(length in 0..samples.size)
        for (index in 0 until length) {
            val sample = samples[index].toInt()
            peak = maxOf(peak, abs(sample))
            energy += sample.toLong() * sample
        }
        count += length
    }
}

/** Vosk and encrypted playback use little-endian PCM16 bytes. */
fun pcm16Bytes(samples: ShortArray, length: Int): ByteArray {
    require(length in 0..samples.size)
    return ByteArray(length * 2).also { bytes ->
        for (index in 0 until length) {
            val value = samples[index].toInt()
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value ushr 8).toByte()
        }
    }
}
