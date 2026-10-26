package com.codecrafters.leaktrace.ui.calls

/** Pure call-state reducer; a user arms exactly one SIM call capture. */
class CallCapturePolicy {
    enum class Phase { Waiting, Recording, Finished }
    enum class Action { None, Start, Complete, Abort }
    var phase = Phase.Waiting
        private set
    private var offhookSeen = false

    fun onCallState(state: Int, speaker: Boolean): Action {
        if (phase == Phase.Finished) return Action.None
        return when (state) {
            OFFHOOK -> {
                offhookSeen = true
                if (phase == Phase.Waiting && speaker) { phase = Phase.Recording; Action.Start }
                else Action.None
            }
            IDLE -> {
                if (!offhookSeen) Action.None
                else {
                    val wasRecording = phase == Phase.Recording
                    phase = Phase.Finished
                    if (wasRecording) Action.Complete else Action.Abort
                }
            }
            else -> Action.None // Ringing or call waiting is not the end of an active call.
        }
    }

    fun onRoute(speaker: Boolean): Action {
        if (phase == Phase.Finished) return Action.None
        if (phase == Phase.Recording && !speaker) {
            phase = Phase.Finished
            return Action.Abort
        }
        if (phase == Phase.Waiting && offhookSeen && speaker) {
            phase = Phase.Recording
            return Action.Start
        }
        return Action.None
    }
    fun stop(): Action {
        val action = if (phase == Phase.Recording) Action.Abort else Action.None
        phase = Phase.Finished
        return action
    }
    fun isWaitingForSpeaker() = phase == Phase.Waiting && offhookSeen
    companion object { const val IDLE = 0; const val RINGING = 1; const val OFFHOOK = 2 }
}
