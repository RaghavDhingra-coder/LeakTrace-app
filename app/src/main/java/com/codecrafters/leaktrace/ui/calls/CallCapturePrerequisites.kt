package com.codecrafters.leaktrace.ui.calls

/** Checks before microphone foreground service startup; rechecked after Android starts it. */
object CallCapturePrerequisites {
    fun problem(owner: Boolean, sdk: Int, microphone: Boolean, callState: Boolean,
                notifications: Boolean, telephony: Boolean): String? = when {
        !owner -> "Sign in before arming call capture."
        sdk < 31 -> "Automatic SIM call monitoring requires Android 12 or newer."
        !telephony -> "This device does not support SIM calling."
        !microphone -> "Microphone permission is required for call capture."
        !callState -> "Call-state permission is required to stop when the SIM call ends."
        !notifications -> "Allow notifications so recording remains visibly indicated."
        else -> null
    }
}

/** A successful AudioRecord state is insufficient: reject no, short, or near-silent samples. */
object CallAudioQuality {
    fun problem(completed: Boolean, bytes: Int, peak: Int, rms: Int, frameworkSilenced: Boolean, failure: String?): String? = when {
        frameworkSilenced -> "Android silenced microphone capture during this call. This device may block third-party call recording; use audio import."
        failure != null -> failure
        !completed -> "Recording was interrupted; partial audio was discarded."
        bytes < 32_000 -> "The recording was too short to verify; audio was discarded."
        peak < 96 || rms < 32 -> "Microphone input was silent or near-silent. Android may block capture during SIM calls on this device; audio was discarded. Use audio import."
        else -> null
    }
}

/** Recording evidence must be reviewed for both voices before account analysis. */
object CallRecordingReview {
    fun canSubmit(source: String?, reviewedSource: String?): Boolean =
        source?.startsWith("recording:") != true || reviewedSource == source
}
