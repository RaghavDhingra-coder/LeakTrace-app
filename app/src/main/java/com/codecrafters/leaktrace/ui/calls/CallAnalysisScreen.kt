package com.codecrafters.leaktrace.ui.calls

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.text.DateFormat
import java.util.Date
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codecrafters.leaktrace.data.remote.CallAnalysisDto
import com.codecrafters.leaktrace.data.remote.CallAnalysisPageDto
import com.codecrafters.leaktrace.ui.components.ExposureCard
import com.codecrafters.leaktrace.ui.components.message
import com.codecrafters.leaktrace.ui.mobile.MobileLoadState
import com.codecrafters.leaktrace.ui.mobile.MobileSessionState

@Composable
fun CallAnalysisScreen(
    session: MobileSessionState,
    result: MobileLoadState<CallAnalysisDto>,
    saved: MobileLoadState<CallAnalysisPageDto>,
    onAnalyze: (String, Boolean, Boolean) -> Unit,
    onRefresh: () -> Unit,
    onDelete: (Long) -> Unit,
    onClearResult: () -> Unit,
) {
    val context = LocalContext.current
    val capture by CallCaptureBus.state.collectAsStateWithLifecycle()
    val transcriptionState by CallTranscriptionBus.state.collectAsStateWithLifecycle()
    val connected = session as? MobileSessionState.Connected
    val owner = connected?.account?.email?.let(CallAudioVault::ownerKey)
    var transcript by remember(owner) { mutableStateOf("") }
    var consent by remember(owner) { mutableStateOf(false) }
    var uploadConsent by remember(owner) { mutableStateOf(false) }
    var save by remember(owner) { mutableStateOf(false) }
    var storeText by remember(owner) { mutableStateOf(false) }
    var importUri by remember(owner) { mutableStateOf<Uri?>(null) }
    var transcribedSource by remember(owner) { mutableStateOf<String?>(null) }
    var submittedSource by remember(owner) { mutableStateOf<String?>(null) }
    var playedSource by remember(owner) { mutableStateOf<String?>(null) }
    var reviewedSource by remember(owner) { mutableStateOf<String?>(null) }
    var localAudioFiles by remember(owner) { mutableStateOf(owner?.let { CallAudioVault.list(context, it) } ?: emptyList()) }
    LaunchedEffect(capture, owner) {
        val ready = capture as? CallCaptureState.Ready
        if (ready != null && ready.owner == owner) {
            importUri = null
            localAudioFiles = CallAudioVault.list(context, ready.owner)
        }
    }
    val startCapture = {
        try { ContextCompat.startForegroundService(context,
            Intent(context, CallCaptureService::class.java).setAction(CallCaptureService.START)
                .putExtra(CallCaptureService.OWNER, owner)) }
        catch (_: Exception) { CallCaptureBus.state.value = CallCaptureState.Error("This device blocked microphone capture.") }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) startCapture()
        else CallCaptureBus.state.value = CallCaptureState.Error("Microphone, call-state, and visible-notification permissions are required to arm call capture.")
    }
    val capturePermissions = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE)
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        importUri = uri
        if (uri != null) CallTranscriptionBus.startImport(context, owner, uri)
    }
    val visibleTranscription = when (val state = transcriptionState) {
        is CallTranscriptionState.Processing -> state.takeIf { it.owner == owner }
        is CallTranscriptionState.Ready -> state.takeIf { it.owner == owner }
        is CallTranscriptionState.Error -> state.takeIf { it.owner == owner }
        else -> null
    }
    val sourceKey = when (val state = visibleTranscription) {
        is CallTranscriptionState.Processing -> state.source
        is CallTranscriptionState.Ready -> state.source
        is CallTranscriptionState.Error -> state.source
        else -> null
    }
    val transcriptionBusy = visibleTranscription is CallTranscriptionState.Processing
    val transcriptionError = (visibleTranscription as? CallTranscriptionState.Error)?.message
    LaunchedEffect(visibleTranscription) {
        when (val state = visibleTranscription) {
            is CallTranscriptionState.Processing -> if (transcribedSource != state.source) {
                transcript = ""
                uploadConsent = false
                submittedSource = null
                playedSource = null
                reviewedSource = null
                transcribedSource = null
                onClearResult()
            }
            is CallTranscriptionState.Ready -> if (transcribedSource != state.source) {
                transcript = state.transcript
                transcribedSource = state.source
            }
            else -> Unit
        }
    }
    val canAnalyze = connected?.account?.scopes?.contains("call:analyze") == true
    val reviewOkay = CallRecordingReview.canSubmit(sourceKey, reviewedSource)
    LaunchedEffect(owner, sourceKey, transcribedSource, uploadConsent, canAnalyze, transcriptionBusy, reviewOkay) {
        if (sourceKey != null && transcribedSource == sourceKey && !transcriptionBusy &&
            uploadConsent && canAnalyze && reviewOkay && submittedSource != sourceKey && transcript.isNotBlank()) {
            submittedSource = sourceKey
            onAnalyze(transcript, save, storeText)
        }
    }
    val canSave = connected?.account?.scopes?.contains("call:write") == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Call leak analysis", style = MaterialTheme.typography.headlineSmall)
        Text("A name or alias spoken by a caller can suggest where you used it. It cannot verify the caller or prove a website leaked data.")
        Text("Live capture is experimental: only the microphone on an active SIM speakerphone call is attempted. The other person may not be audible. Check local recording and consent laws before use.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = consent, onCheckedChange = { consent = it })
            Text("I have consent to record this call and understand the microphone indicator will stay visible.")
        }
        val active = capture == CallCaptureState.WaitingForCall ||
            capture == CallCaptureState.WaitingForSpeaker || capture == CallCaptureState.Recording
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Arm SIM speakerphone capture")
            Switch(checked = active, enabled = active || (consent && owner != null && Build.VERSION.SDK_INT >= 31), onCheckedChange = { enabled ->
                if (enabled) {
                    if (Build.VERSION.SDK_INT < 31) CallCaptureBus.state.value = CallCaptureState.Error("Live capture requires Android 12 or newer.")
                    else if (capturePermissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) startCapture()
                    else permissions.launch(capturePermissions)
                } else try {
                    context.startService(Intent(context, CallCaptureService::class.java).setAction(CallCaptureService.STOP))
                } catch (_: Exception) {
                    CallCaptureBus.state.value = CallCaptureState.Error("Could not cancel call monitoring. Check the recording notification.")
                }
            })
        }
        when (val state = capture) {
            CallCaptureState.WaitingForCall -> Text("Armed and waiting for a SIM call. Recording starts when the call uses speakerphone. Cancel at any time.")
            CallCaptureState.WaitingForSpeaker -> Text("SIM call detected. Switch to speakerphone to start microphone recording automatically.")
            CallCaptureState.Recording -> Text("Microphone active. Both sides are unverified until you play the result. Cancel in the app or notification.", color = MaterialTheme.colorScheme.error)
            is CallCaptureState.Ready -> if (owner == state.owner) {
                Text("Audio passed a basic silence check, but both sides are unverified. Play it to confirm before relying on the offline transcript.")
            }
            is CallCaptureState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        if (connected != null) OutlinedButton(enabled = !active, onClick = {
            CallAudioPlayback.stop()
            CallAudioVault.deleteAll(context, owner!!)
            localAudioFiles = emptyList()
            CallCaptureBus.state.value = CallCaptureState.Idle
            CallTranscriptionBus.clear()
            onClearResult(); transcript = ""
        }) { Text("Delete all local call recordings (${localAudioFiles.size})") }
        if (owner != null && localAudioFiles.isNotEmpty()) {
            Text("Encrypted recordings on this device", style = MaterialTheme.typography.titleMedium)
            localAudioFiles.forEach { file ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(DateFormat.getDateTimeInstance().format(Date(file.lastModified())))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                if (CallAudioPlayback.play(context, owner, file.absolutePath))
                                    playedSource = "recording:${file.absolutePath}"
                            }) { Text("Play") }
                            OutlinedButton(onClick = { CallTranscriptionBus.startRecording(context, owner, file.absolutePath) }) { Text("Transcribe") }
                            OutlinedButton(onClick = {
                                CallAudioPlayback.stop()
                                CallAudioVault.delete(context, owner, file.absolutePath)
                                localAudioFiles = CallAudioVault.list(context, owner)
                                CallTranscriptionBus.clear()
                                onClearResult(); transcript = ""
                            }) { Text("Delete") }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { import.launch(arrayOf("audio/*")) }) { Text("Choose an existing audio file") }
        importUri?.let { uri ->
            Text("Audio selected locally. Offline transcription supports 16 kHz mono 16-bit PCM WAV. The file is never uploaded.")
            OutlinedButton(onClick = { CallAudioPlayback.playImport(context, uri) }) { Text("Play selected audio locally") }
            OutlinedButton(onClick = { importUri = null; CallTranscriptionBus.clear(); onClearResult(); transcript = "" }) { Text("Clear audio selection") }
        }
        if (transcriptionBusy) Text("Transcribing on this device…", color = MaterialTheme.colorScheme.primary)
        transcriptionError?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { CallTranscriptionBus.retry(context) }) { Text("Retry offline transcription") }
        }
        if (transcribedSource == sourceKey && sourceKey != null) Text("Offline transcript ready. Check it before relying on the analysis.")
        if (sourceKey?.startsWith("recording:") == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = reviewedSource == sourceKey,
                    enabled = playedSource == sourceKey,
                    onCheckedChange = { reviewedSource = if (it) sourceKey else null })
                Text("I played this recording and can hear both sides. If a voice is missing, delete it or import a complete audio file.")
            }
            if (!reviewOkay) Text("Call audio is unverified; account analysis waits for your playback review.",
                color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(value = transcript, onValueChange = { transcript = it.take(8000); onClearResult() },
            label = { Text("Transcript to analyze") }, modifier = Modifier.fillMaxWidth(), minLines = 5,
            supportingText = { Text("Enter or correct the transcript. Speech and names may be inaccurate.") })
        if (!canAnalyze) Text("Sign in or pair a device with Call Analysis access to compare this transcript with your account.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = uploadConsent, onCheckedChange = { uploadConsent = it })
            Text("After offline transcription, send only this transcript text to LeakTrace for account comparison. Audio stays on this device.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = save, enabled = canSave, onCheckedChange = { save = it; if (!it) storeText = false })
            Text("Save the analysis in my account")
        }
        if (save) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = storeText, onCheckedChange = { storeText = it })
            Text("Also save the redacted transcript on the server")
        }
        Button(onClick = { onAnalyze(transcript, save, storeText) },
            enabled = canAnalyze && uploadConsent && reviewOkay && transcript.isNotBlank() && !transcriptionBusy && result != MobileLoadState.Loading) {
            Text("Analyze or reanalyze transcript")
        }
        when (result) {
            MobileLoadState.Loading -> Text("Comparing transcript with your account…")
            is MobileLoadState.Ready -> CallResult(result.value)
            is MobileLoadState.Error -> Text("Analysis failed: ${result.reason.message()}", color = MaterialTheme.colorScheme.error)
            MobileLoadState.Unavailable -> Text("Call Analysis is not deployed on this server.")
            MobileLoadState.SessionExpired -> Text("Session expired. Sign in again.")
            else -> Unit
        }
        Text("Saved analyses", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onRefresh, enabled = canAnalyze) { Text("Refresh saved analyses") }
        when (saved) {
            is MobileLoadState.Ready -> saved.value.items.forEach { item ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item.createdAt ?: "Saved call"} · ${item.status}")
                    Text(item.redactedTranscript.ifBlank { "Transcript was not saved." })
                    item.exposure.forEach { ExposureCard(it) }
                    item.id?.let { id -> OutlinedButton(onClick = { onDelete(id) }) { Text("Delete saved analysis") } }
                } }
            }
            MobileLoadState.Empty -> Text("No saved analyses.")
            MobileLoadState.Loading -> Text("Loading saved analyses…")
            is MobileLoadState.Error -> Text("Could not load saved analyses: ${saved.reason.message()}")
            else -> Unit
        }
    }
}

@Composable
private fun CallResult(result: CallAnalysisDto) {
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Call analysis · ${result.status}", style = MaterialTheme.typography.titleMedium)
        Text(result.notice, style = MaterialTheme.typography.bodySmall)
        if (result.exposure.isEmpty()) Text("No possible source finding from the available evidence.")
        else result.exposure.forEach { ExposureCard(it) }
        Text("Extracted details", style = MaterialTheme.typography.titleSmall)
        result.entities.forEach { Text("${it.type}: ${it.display}${it.relation?.let { relation -> " · $relation" } ?: ""}") }
        Text("Relevant statements", style = MaterialTheme.typography.titleSmall)
        if (result.statements.isEmpty()) Text("No supported warning statement found.")
        else result.statements.forEach { Text(it.message) }
        result.candidates.forEach { candidate ->
            Text("${candidate.name} · ${candidate.status} · ${candidate.emailCount ?: 0} related emails · ${candidate.smsCount ?: 0} saved SMS findings")
            candidate.signals.forEach { Text("• ${it.explanation}") }
        }
        result.missing.forEach { Text("Uncertain: $it", style = MaterialTheme.typography.bodySmall) }
    } }
}
