package com.codecrafters.leaktrace

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.codecrafters.leaktrace.ui.navigation.LeakTraceApp
import com.codecrafters.leaktrace.ui.theme.LeakTraceTheme

class MainActivity : ComponentActivity() {
    // Text shared into LeakTrace (e.g. an SMS shared from a messages app), waiting to be shown.
    private var sharedText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch carries a new share; after recreation it was already handled.
        if (savedInstanceState == null) sharedText = intent.sharedText()
        setContent {
            LeakTraceTheme {
                LeakTraceApp(sharedText = sharedText, onSharedTextConsumed = { sharedText = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.sharedText()?.let { sharedText = it }
    }
}

private fun Intent.sharedText(): String? =
    if (action == Intent.ACTION_SEND && type == "text/plain") {
        getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
    } else {
        null
    }
