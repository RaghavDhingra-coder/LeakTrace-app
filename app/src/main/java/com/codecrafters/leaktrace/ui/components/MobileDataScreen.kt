package com.codecrafters.leaktrace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.ui.mobile.MobileFailure
import com.codecrafters.leaktrace.ui.mobile.MobileLoadState

/** Authenticated request results take precedence over a stale or failed discovery probe. */
internal fun showMobileData(state: MobileLoadState<*>, availability: com.codecrafters.leaktrace.data.server.FeatureAvailability?): Boolean =
    state != MobileLoadState.NeedsAccount || availability == com.codecrafters.leaktrace.data.server.FeatureAvailability.RequiresSignIn

@Composable
fun <T> MobileDataScreen(
    description: String,
    state: MobileLoadState<T>,
    onRetry: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(description, style = MaterialTheme.typography.bodyLarge)
        when (state) {
            MobileLoadState.Loading -> {
                CircularProgressIndicator()
                Text("Loading your account data…")
            }
            MobileLoadState.Empty -> {
                EmptyState("Nothing here yet", "Your account returned no records for this section.")
                Button(onClick = onRetry) { Text("Refresh") }
            }
            MobileLoadState.NeedsAccount -> {
                EmptyState("Connect your account", "Pair this device with your LeakTrace dashboard to load private data.")
                Button(onClick = onOpenAccount) { Text("Account") }
            }
            MobileLoadState.SessionExpired -> {
                EmptyState("Session expired", "Pair this device again to restore access.")
                Button(onClick = onOpenAccount) { Text("Account") }
            }
            MobileLoadState.Unavailable -> {
                EmptyState("Feature unavailable", "This server does not provide the mobile data route.")
                Button(onClick = onRetry) { Text("Try again") }
            }
            is MobileLoadState.Error -> {
                EmptyState("Could not load data", state.reason.message())
                Button(onClick = onRetry) { Text("Try again") }
            }
            is MobileLoadState.Ready -> content(state.value)
        }
    }
}

fun MobileFailure.message(): String = when (this) {
    MobileFailure.Network -> "Check your connection and try again."
    MobileFailure.RateLimited -> "Too many requests. Wait a moment and try again."
    MobileFailure.InvalidResponse -> "The server returned data the app could not read."
    MobileFailure.Storage -> "Secure session storage is unavailable on this device."
    MobileFailure.Forbidden -> "This account does not have access to this feature."
    MobileFailure.InvalidRequest -> "The server rejected this request."
    MobileFailure.Server -> "The server could not complete the request."
}
