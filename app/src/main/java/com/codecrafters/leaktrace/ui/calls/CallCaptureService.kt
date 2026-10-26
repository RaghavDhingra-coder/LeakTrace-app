package com.codecrafters.leaktrace.ui.calls

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicBoolean

sealed interface CallCaptureState {
    data object Idle : CallCaptureState
    data object WaitingForCall : CallCaptureState
    data object WaitingForSpeaker : CallCaptureState
    data object Recording : CallCaptureState
    data class Ready(val path: String, val owner: String) : CallCaptureState
    data class Error(val message: String) : CallCaptureState
}

object CallCaptureBus { val state = MutableStateFlow<CallCaptureState>(CallCaptureState.Idle) }

/** A user-started foreground service observes SIM call state and records only microphone audio. */
class CallCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val reading = AtomicBoolean(false)
    private val policy = CallCapturePolicy()
    private val callbacks = mutableListOf<Pair<TelephonyManager, TelephonyCallback>>()
    private val states = mutableMapOf<Int, Int>()
    private var recorder: AudioRecord? = null
    private var owner: String? = null
    private var armed = false
    private var ending = false
    private var foreground = false
    private var lastRoute: String? = null
    private var lastCombinedState: Int? = null
    private var configCallback: AudioManager.AudioRecordingCallback? = null
    @Volatile private var frameworkSilenced = false
    @Volatile private var completion = false
    @Volatile private var failure: String? = null
    private val routeCheck = object : Runnable {
        override fun run() {
            if (ending || !armed) return
            val permissionsOkay = ContextCompat.checkSelfPermission(this@CallCaptureService, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this@CallCaptureService, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED &&
                (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this@CallCaptureService, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            if (!permissionsOkay) {
                finish(false, "Call capture permission was removed; partial audio was discarded.", discard = true)
                return
            }
            handle(policy.onRoute(speakerphone()))
            if (policy.isWaitingForSpeaker()) setStatus(CallCaptureState.WaitingForSpeaker)
            handler.postDelayed(this, 1_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService request must promote before any validation/early return.
        // The old early returns produced ForegroundServiceDidNotStartInTimeException on device.
        if (!foreground && !promote()) {
            CallCaptureBus.state.value = CallCaptureState.Error("Android blocked the microphone foreground service. Check app permissions and try again.")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (ending) {
            if (intent?.action == START)
                CallCaptureBus.state.value = CallCaptureState.Error("The previous capture is still stopping. Try again in a moment.")
            return START_NOT_STICKY
        }
        when (intent?.action) {
            STOP, DISCARD -> {
                finish(false, null, discard = true)
                return START_NOT_STICKY
            }
            START -> if (armed) return START_NOT_STICKY
            else -> {
                finish(false, "Recording could not be started.", discard = true)
                return START_NOT_STICKY
            }
        }
        owner = intent.getStringExtra(OWNER)?.takeIf { it.isNotBlank() }
        val problem = CallCapturePrerequisites.problem(
            owner != null, Build.VERSION.SDK_INT,
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED,
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CALLING),
        )
        if (problem != null) {
            Log.w(TAG, "capture_prerequisite_failed category=${problem.substringBefore(' ')}")
            finish(false, problem, discard = true)
            return START_NOT_STICKY
        }
        try {
            armed = true
            setStatus(CallCaptureState.WaitingForCall)
            if (ending) return START_NOT_STICKY
            registerCallStates()
            handler.post(routeCheck)
        } catch (_: Exception) {
            finish(false, "SIM call monitoring is unavailable on this device.", discard = true)
        }
        return START_NOT_STICKY
    }

    private fun promote(): Boolean = try {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION_ID, notification("Waiting for a SIM call · tap Cancel"),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        foreground = true
        true
    } catch (_: Exception) { false }

    private fun notification(message: String): Notification {
        val cancel = PendingIntent.getService(this, 2,
            Intent(this, CallCaptureService::class.java).setAction(STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("LeakTrace call capture")
            .setContentText(message)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_media_pause, "Cancel", cancel).build())
            .build()
    }

    private fun setStatus(status: CallCaptureState) {
        CallCaptureBus.state.value = status
        val message = when (status) {
            CallCaptureState.WaitingForCall -> "Waiting for a SIM call · tap Cancel"
            CallCaptureState.WaitingForSpeaker -> "Call detected · switch to speakerphone"
            CallCaptureState.Recording -> "Microphone active · audio not yet verified · tap Cancel"
            else -> return
        }
        if (foreground) try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(message))
        } catch (_: Exception) {
            finish(false, "The recording notification could not be shown; partial audio was discarded.", discard = true)
        }
    }

    private fun registerCallStates() {
        val base = getSystemService(TelephonyManager::class.java)
        val ids = try { getSystemService(SubscriptionManager::class.java).activeSubscriptionInfoList
            ?.map { it.subscriptionId }?.distinct()?.take(4).orEmpty() }
            catch (_: SecurityException) { emptyList() }
        val targets = if (ids.isEmpty()) listOf(-1 to base)
            else ids.map { it to base.createForSubscriptionId(it) }
        targets.forEach { (id, manager) ->
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    states[id] = state
                    updateCallState()
                }
            }
            states[id] = manager.callState
            manager.registerTelephonyCallback(mainExecutor, callback)
            callbacks += manager to callback
        }
        updateCallState()
    }

    private fun updateCallState() {
        if (ending) return
        val state = when {
            states.values.any { it == TelephonyManager.CALL_STATE_OFFHOOK } -> CallCapturePolicy.OFFHOOK
            states.values.any { it == TelephonyManager.CALL_STATE_RINGING } -> CallCapturePolicy.RINGING
            else -> CallCapturePolicy.IDLE
        }
        if (state != lastCombinedState) {
            Log.i(TAG, "sim_call_state=$state")
            lastCombinedState = state
        }
        handle(policy.onCallState(state, speakerphone()))
        if (policy.isWaitingForSpeaker()) setStatus(CallCaptureState.WaitingForSpeaker)
    }

    private fun speakerphone(): Boolean = try {
        val audio = getSystemService(AudioManager::class.java)
        val selected = audio.communicationDevice
        val speaker = if (selected != null) selected.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else audio.isSpeakerphoneOn
        val route = "mode=${audio.mode} communication_type=${selected?.type ?: -1} speaker=$speaker"
        if (route != lastRoute) { Log.i(TAG, "audio_route $route"); lastRoute = route }
        speaker
    } catch (error: Exception) {
        Log.w(TAG, "audio_route_error class=${error.javaClass.simpleName}")
        false
    }

    private fun reportRecordingConfiguration(record: AudioRecord) {
        val configuration = try { record.activeRecordingConfiguration } catch (_: Exception) { null }
        if (configuration?.isClientSilenced == true) frameworkSilenced = true
        Log.i(TAG, "recording_configuration silenced=${configuration?.isClientSilenced} " +
            "client_source=${configuration?.clientAudioSource} input_type=${configuration?.audioDevice?.type ?: -1} " +
            "client_rate=${configuration?.clientFormat?.sampleRate ?: -1} " +
            "device_rate=${configuration?.format?.sampleRate ?: -1}")
    }

    private fun handle(action: CallCapturePolicy.Action) {
        when (action) {
            CallCapturePolicy.Action.Start -> startMicrophone()
            CallCapturePolicy.Action.Complete -> finish(true, null, discard = false)
            CallCapturePolicy.Action.Abort -> finish(false,
                if (reading.get()) "Speakerphone changed or the call was interrupted; partial audio was discarded."
                else "The call ended before supported speakerphone capture began.", discard = true)
            CallCapturePolicy.Action.None -> Unit
        }
    }

    private fun startMicrophone() {
        if (ending || !reading.compareAndSet(false, true)) return
        try {
            val minimum = AudioRecord.getMinBufferSize(16_000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT)
            if (minimum <= 0) throw IllegalStateException("Unsupported audio format")
            val size = maxOf(4096, minimum)
            Log.i(TAG, "recorder_init source=MIC rate=16000 channels=1 encoding=PCM16 min_buffer=$minimum buffer=${size * 2}")
            val record = AudioRecord(MediaRecorder.AudioSource.MIC, 16_000, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, size * 2)
            Log.i(TAG, "recorder_state=${record.state}")
            if (record.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("Microphone unavailable")
            recorder = record
            val callback = object : AudioManager.AudioRecordingCallback() {
                override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                    reportRecordingConfiguration(record)
                }
            }
            record.registerAudioRecordingCallback(mainExecutor, callback)
            configCallback = callback
            record.startRecording()
            Log.i(TAG, "recording_state=${record.recordingState}")
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IllegalStateException("Microphone busy")
            reportRecordingConfiguration(record)
            val callOwner = owner ?: throw IllegalStateException("Account changed")
            setStatus(CallCaptureState.Recording)
            if (ending) throw IllegalStateException("Recording notification unavailable")
            val (partial, output) = CallAudioVault.create(this, callOwner)
            Thread {
                var bytes = 0
                val metrics = CallPcmMetrics()
                var emptyReads = 0
                var lastDiagnosticBytes = 0
                try {
                    output.use { encrypted ->
                        val samples = ShortArray(size / 2)
                        while (reading.get()) {
                            if (frameworkSilenced) { reading.set(false); break }
                            val count = record.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                            if (count < 0) {
                                Log.w(TAG, "recorder_read_error code=$count")
                                if (!reading.get()) break
                                throw IllegalStateException("AudioRecord read error")
                            }
                            if (count == 0) {
                                if (++emptyReads > 150) throw IllegalStateException("No microphone samples")
                                Thread.sleep(20)
                                continue
                            }
                            emptyReads = 0
                            val pcm = pcm16Bytes(samples, count)
                            encrypted.write(pcm)
                            metrics.add(samples, count)
                            bytes += pcm.size
                            if (bytes - lastDiagnosticBytes >= 160_000) {
                                Log.i(TAG, "capture_levels bytes=$bytes peak=${metrics.peak} rms=${metrics.rms} silenced=$frameworkSilenced")
                                lastDiagnosticBytes = bytes
                            }
                            if (bytes >= 30_000_000) throw IllegalStateException("Recording limit reached")
                        }
                    }
                    Log.i(TAG, "capture_final bytes=$bytes samples=${metrics.count} peak=${metrics.peak} " +
                        "rms=${metrics.rms} silenced=$frameworkSilenced completed=$completion")
                    val problem = CallAudioQuality.problem(completion, bytes, metrics.peak, metrics.rms,
                        frameworkSilenced, failure)
                    if (problem != null) {
                        CallAudioVault.delete(this, callOwner, partial.absolutePath)
                        CallCaptureBus.state.value = CallCaptureState.Error(problem)
                    } else {
                        val path = CallAudioVault.complete(this, callOwner, partial.absolutePath)
                        CallCaptureBus.state.value = CallCaptureState.Ready(path, callOwner)
                        CallTranscriptionBus.startRecording(this, callOwner, path)
                    }
                } catch (error: Exception) {
                    Log.e(TAG, "capture_failure class=${error.javaClass.simpleName} silenced=$frameworkSilenced")
                    CallAudioVault.delete(this, callOwner, partial.absolutePath)
                    CallCaptureBus.state.value = CallCaptureState.Error(
                        if (frameworkSilenced) CallAudioQuality.problem(false, bytes, metrics.peak, metrics.rms, true, null).orEmpty()
                        else failure ?: "Microphone capture failed; partial audio was discarded.")
                } finally {
                    configCallback?.let { try { record.unregisterAudioRecordingCallback(it) } catch (_: Exception) {} }
                    configCallback = null
                    record.release()
                    recorder = null
                    handler.post {
                        if (!ending) finish(false, "Microphone capture failed; partial audio was discarded.", discard = true)
                        else cleanupAndStop()
                    }
                }
            }.start()
        } catch (error: Exception) {
            Log.e(TAG, "recorder_start_failure class=${error.javaClass.simpleName}")
            configCallback?.let { callback -> try { recorder?.unregisterAudioRecordingCallback(callback) } catch (_: Exception) {} }
            configCallback = null
            try { recorder?.release() } catch (_: Exception) {}
            recorder = null
            reading.set(false)
            if (ending) cleanupAndStop()
            else finish(false, "Microphone capture is unavailable during this call.", discard = true)
        }
    }

    private fun finish(callEnded: Boolean, message: String?, discard: Boolean) {
        if (ending) return
        ending = true
        armed = false
        completion = callEnded && !discard
        Log.i(TAG, "capture_finish call_ended=$callEnded discard=$discard")
        failure = message
        reading.set(false)
        try { recorder?.stop() } catch (_: Exception) { /* Read loop handles stopped recorder. */ }
        handler.removeCallbacks(routeCheck)
        callbacks.forEach { (manager, callback) ->
            try { manager.unregisterTelephonyCallback(callback) } catch (_: Exception) {}
        }
        callbacks.clear()
        if (recorder == null) {
            CallCaptureBus.state.value = if (message != null) CallCaptureState.Error(message) else CallCaptureState.Idle
            cleanupAndStop()
        }
    }

    private fun cleanupAndStop() {
        if (foreground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (!ending) finish(false, "Recording was interrupted; partial audio was discarded.", discard = true)
        super.onDestroy()
    }

    companion object {
        const val START = "leaktrace.call.START"
        const val STOP = "leaktrace.call.STOP"
        const val DISCARD = "leaktrace.call.DISCARD"
        const val OWNER = "leaktrace.call.OWNER"
        private const val CHANNEL = "leaktrace_call_capture"
        private const val NOTIFICATION_ID = 42
        private const val TAG = "LeakTraceCallCapture"
    }
}

