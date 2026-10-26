package com.codecrafters.leaktrace.ui.calls

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow

sealed interface CallTranscriptionState {
    data object Idle : CallTranscriptionState
    data class Processing(val owner: String?, val source: String) : CallTranscriptionState
    data class Ready(val owner: String?, val source: String, val transcript: String) : CallTranscriptionState
    data class Error(val owner: String?, val source: String, val message: String) : CallTranscriptionState
}

/** Keeps offline decoding alive after the screen closes. Results remain in memory, not on disk. */
object CallTranscriptionBus {
    val state = MutableStateFlow<CallTranscriptionState>(CallTranscriptionState.Idle)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var task: Task? = null

    private sealed interface Task {
        val owner: String?
        val source: String
        suspend fun run(context: Context): String
    }
    private data class Recording(override val owner: String, val path: String) : Task {
        override val source = "recording:$path"
        override suspend fun run(context: Context) = transcribeRecording(context, owner, path)
    }
    private data class Imported(override val owner: String?, val uri: Uri) : Task {
        override val source = "import:$uri"
        override suspend fun run(context: Context) = transcribeImport(context, uri)
    }

    @Synchronized fun startRecording(context: Context, owner: String, path: String) = start(context, Recording(owner, path))
    @Synchronized fun startImport(context: Context, owner: String?, uri: Uri) = start(context, Imported(owner, uri))
    @Synchronized fun retry(context: Context) { task?.let { start(context, it) } }
    @Synchronized fun clear() {
        job?.cancel()
        job = null
        task = null
        state.value = CallTranscriptionState.Idle
    }
    private fun start(context: Context, requested: Task) {
        job?.cancel()
        task = requested
        state.value = CallTranscriptionState.Processing(requested.owner, requested.source)
        val app = context.applicationContext
        job = scope.launch {
            try {
                val transcript = withContext(Dispatchers.IO) { requested.run(app) }
                state.value = CallTranscriptionState.Ready(requested.owner, requested.source, transcript.take(8000))
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: EmptyCallAudioException) {
                state.value = CallTranscriptionState.Error(requested.owner, requested.source, error.message.orEmpty())
            } catch (error: UnsupportedCallAudioException) {
                state.value = CallTranscriptionState.Error(requested.owner, requested.source, error.message.orEmpty())
            } catch (_: Exception) {
                state.value = CallTranscriptionState.Error(requested.owner, requested.source,
                    "Offline transcription failed. Check audio and retry.")
            }
        }
    }
}
