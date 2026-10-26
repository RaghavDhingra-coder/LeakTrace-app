package com.codecrafters.leaktrace.ui.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.codecrafters.leaktrace.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private suspend fun readInbox(context: Context): List<DeviceSms> = withContext(Dispatchers.IO) {
    val projection = arrayOf("_id", "thread_id", "address", "body", "date")
    val result = mutableListOf<DeviceSms>()
    val cursor = context.contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI, projection, null, null, "date DESC")
        ?: throw IllegalStateException("Inbox provider unavailable")
    cursor.use {
        while (result.size < 200 && it.moveToNext()) {
            result.add(DeviceSms(it.getLong(0), it.getLong(1), it.getString(2), it.getString(3).orEmpty(), it.getLong(4)))
        }
    }
    result
}

@Composable
fun SmsInboxScreen(onBack: () -> Unit, serverAnalysisAvailable: Boolean, onCheck: (DeviceSms) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember { mutableStateOf(false) }
    var active by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var messages by remember { mutableStateOf<List<DeviceSms>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        if (!granted) error = "SMS access was not granted. You can still paste or share one message for local analysis."
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                active = true
                allowed = BuildConfig.LOCAL_SMS_INBOX && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
                refresh++
            } else if (event == Lifecycle.Event.ON_STOP) { active = false; messages = emptyList() }
        }
        lifecycle.addObserver(observer)
        active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        allowed = BuildConfig.LOCAL_SMS_INBOX && ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(allowed, active, refresh) {
        messages = emptyList()
        if (allowed && active && BuildConfig.LOCAL_SMS_INBOX) {
            loading = true
            error = null
            try { messages = readInbox(context) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: SecurityException) { allowed = false; error = "Android or your installer restricted SMS access. Paste or share a message instead." }
            catch (_: Exception) { error = "Could not read the device inbox. Try again or paste a message." }
            finally { loading = false }
        }
    }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Device SMS inbox", style = MaterialTheme.typography.headlineSmall)
            Text(if (serverAnalysisAvailable) "Up to 200 recent incoming messages, grouped by conversation. Reading stays on this device. Tapping Check analyzes only the selected message locally and sends that message and sender to LeakTrace for account comparison." else "Up to 200 recent incoming messages, grouped by conversation. Reading and Check stay on this device. Sign in to compare a selected message with your registered sites.")
            OutlinedButton(onClick = onBack) { Text("Back to paste / share analyzer") }
            if (!BuildConfig.LOCAL_SMS_INBOX) {
                Text("Inbox permission is not included in this distribution. Share text from your SMS app or paste one message here. Google Play restricts SMS access to eligible, approved uses.")
            } else if (!allowed) {
                Text("Allow read-only SMS access to display your inbox. LeakTrace cannot send, delete, or respond to messages. Android or the installer may restrict this permission.")
                Button(onClick = { permission.launch(Manifest.permission.READ_SMS) }) { Text("Allow reading SMS") }
            } else {
                OutlinedButton(onClick = { refresh++ }, enabled = !loading) { Text("Refresh inbox") }
            }
            if (loading) { CircularProgressIndicator(); Text("Reading device inbox…") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (allowed && !loading && error == null && messages.isEmpty()) Text("No incoming messages available on this device.")
        }
        groupSms(messages).forEach { conversation ->
            item(key = "thread-${conversation.first().threadId}") {
                Text(conversation.first().sender ?: "Unknown sender", style = MaterialTheme.typography.titleMedium)
            }
            items(conversation, key = { it.id }) { message ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message.sender ?: "Unknown sender")
                        Text(DateFormat.getDateTimeInstance().format(Date(message.timestamp)))
                        Text(message.body)
                        Button(onClick = { onCheck(message) }, enabled = message.body.isNotBlank() && message.body.length <= SmsDraftState.MAX_SMS_LENGTH) {
                            Text(if (message.body.length > SmsDraftState.MAX_SMS_LENGTH) "Too long for analysis" else if (serverAnalysisAvailable) "Check · send this SMS to LeakTrace" else "Check locally")
                        }
                    }
                }
            }
        }
    }
}
