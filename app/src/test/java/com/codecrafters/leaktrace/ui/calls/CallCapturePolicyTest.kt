package com.codecrafters.leaktrace.ui.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCapturePolicyTest {
    @Test fun idleWhileArmedWaitsForLaterCallThenSpeakerStartsAndIdleCompletes() {
        val gate = CallCapturePolicy()
        assertEquals(CallCapturePolicy.Action.None, gate.onCallState(CallCapturePolicy.IDLE, false))
        assertEquals(CallCapturePolicy.Phase.Waiting, gate.phase)
        assertEquals(CallCapturePolicy.Action.None, gate.onCallState(CallCapturePolicy.OFFHOOK, false))
        assertTrue(gate.isWaitingForSpeaker())
        assertEquals(CallCapturePolicy.Action.Start, gate.onRoute(true))
        assertEquals(CallCapturePolicy.Action.None, gate.onCallState(CallCapturePolicy.RINGING, true))
        assertEquals(CallCapturePolicy.Action.Complete, gate.onCallState(CallCapturePolicy.IDLE, true))
        assertEquals(CallCapturePolicy.Action.None, gate.onRoute(true))
    }

    @Test fun existingSpeakerCallStartsImmediatelyAndLossOfSpeakerAborts() {
        val gate = CallCapturePolicy()
        assertEquals(CallCapturePolicy.Action.Start, gate.onCallState(CallCapturePolicy.OFFHOOK, true))
        assertEquals(CallCapturePolicy.Action.Abort, gate.onRoute(false))
        assertEquals(CallCapturePolicy.Action.None, gate.onCallState(CallCapturePolicy.IDLE, false))
    }

    @Test fun callWithoutSpeakerAndUserCancelNeverProduceRecording() {
        val gate = CallCapturePolicy()
        gate.onCallState(CallCapturePolicy.OFFHOOK, false)
        assertEquals(CallCapturePolicy.Action.Abort, gate.onCallState(CallCapturePolicy.IDLE, false))
        val cancelled = CallCapturePolicy()
        assertEquals(CallCapturePolicy.Action.None, cancelled.stop())
        assertEquals(CallCapturePolicy.Action.None, cancelled.onCallState(CallCapturePolicy.OFFHOOK, true))
    }

    @Test fun missingPermissionsAndUnsupportedDeviceFailBeforeArm() {
        fun check(mic: Boolean = true, phone: Boolean = true, notifications: Boolean = true,
                  owner: Boolean = true, sdk: Int = 36, telephony: Boolean = true) =
            CallCapturePrerequisites.problem(owner, sdk, mic, phone, notifications, telephony)
        assertNull(check())
        assertTrue(check(mic = false)!!.contains("Microphone"))
        assertTrue(check(phone = false)!!.contains("Call-state"))
        assertTrue(check(notifications = false)!!.contains("notifications"))
        assertTrue(check(owner = false)!!.contains("Sign in"))
        assertTrue(check(sdk = 30)!!.contains("Android 12"))
        assertTrue(check(telephony = false)!!.contains("SIM"))
    }

    @Test fun unreviewedOrOneSidedRecordingCannotBeSubmittedAsComplete() {
        assertEquals(false, CallRecordingReview.canSubmit("recording:/private/audio", null))
        assertEquals(false, CallRecordingReview.canSubmit("recording:/private/audio", "recording:/other"))
        assertEquals(true, CallRecordingReview.canSubmit("recording:/private/audio", "recording:/private/audio"))
        assertEquals(true, CallRecordingReview.canSubmit("import:local", null))
    }

    @Test fun silenceShortAudioAndInterruptedCaptureNeverCountAsComplete() {
        assertNull(CallAudioQuality.problem(true, 64_000, 500, 100, false, null))
        assertTrue(CallAudioQuality.problem(true, 64_000, 0, 0, false, null)!!.contains("silent"))
        assertTrue(CallAudioQuality.problem(true, 64_000, 500, 1, false, null)!!.contains("near-silent"))
        assertTrue(CallAudioQuality.problem(true, 1_000, 500, 100, false, null)!!.contains("too short"))
        assertTrue(CallAudioQuality.problem(false, 64_000, 500, 100, false, null)!!.contains("interrupted"))
        assertEquals("Microphone failed", CallAudioQuality.problem(true, 64_000, 500, 100, false, "Microphone failed"))
    }
}
