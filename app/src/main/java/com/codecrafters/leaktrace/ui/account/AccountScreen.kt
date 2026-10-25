package com.codecrafters.leaktrace.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.ui.components.message
import com.codecrafters.leaktrace.ui.mobile.MobileFailure
import com.codecrafters.leaktrace.ui.mobile.MobileSessionState
import com.codecrafters.leaktrace.ui.server.AvailabilityChip
import com.codecrafters.leaktrace.ui.server.ServerStatusCard
import com.codecrafters.leaktrace.ui.server.ServerUiState

@Composable
fun AccountScreen(
    serverState: ServerUiState,
    session: MobileSessionState,
    pairingError: MobileFailure?,
    onRetry: () -> Unit,
    onStartPairing: () -> Unit,
    onCheckPairing: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = serverState.availability(BackendFeature.Account) == FeatureAvailability.RequiresSignIn
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Connect your existing LeakTrace account", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text("A mobile-capable LeakTrace server pairs this device through your signed-in web dashboard. Your dashboard password never enters the app.")
        ServerStatusCard(serverState, onRetry)
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    session is MobileSessionState.Connected -> {
                        Text("Connected", style = MaterialTheme.typography.titleMedium)
                        Text(session.account.email)
                        Text("Device: ${session.account.device.label ?: "Android device"}")
                        OutlinedButton(onClick = onSignOut) { Text("Sign out on this device") }
                    }
                    serverState.report == null -> {
                        CircularProgressIndicator()
                        Text("Checking whether this server supports mobile accounts…")
                    }
                    !available && session !is MobileSessionState.Pairing -> {
                        val missing = serverState.accountAccessState() == AccountAccessState.NotDeployed
                        Text(if (missing) "Mobile account API unavailable" else "Could not check mobile account access", style = MaterialTheme.typography.titleMedium)
                        Text(if (missing) "The configured server returned 404 for the mobile account route. Check the server URL."
                             else "Account access could not be confirmed. Check your connection and retry; this does not mean the API is missing.")
                        AvailabilityChip(serverState.availability(BackendFeature.Account))
                        OutlinedButton(onClick = onRetry) { Text("Check again") }
                    }
                    session is MobileSessionState.Checking -> {
                        CircularProgressIndicator()
                        Text("Checking secure session…")
                    }
                    session is MobileSessionState.Unavailable -> {
                        Text("Mobile account API unavailable", style = MaterialTheme.typography.titleMedium)
                        Text("The mobile route did not respond. Check this server again before pairing.")
                        OutlinedButton(onClick = onRetry) { Text("Try again") }
                    }
                    session is MobileSessionState.Pairing -> {
                        Text("Approve this device", style = MaterialTheme.typography.titleMedium)
                        Text("In your LeakTrace web dashboard, open Settings → Connected mobile devices and enter this pairing code:")
                        SelectionContainer { Text(session.code, style = MaterialTheme.typography.headlineMedium) }
                        Text("Expires: ${session.expiresAt}", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onCheckPairing, enabled = !session.checking) { Text(if (session.checking) "Checking…" else "I've approved it — check status") }
                        pairingError?.let { Text(it.message(), color = MaterialTheme.colorScheme.error) }
                    }
                    else -> {
                        Text(when (session) {
                            MobileSessionState.Expired -> "Session or pairing expired"
                            is MobileSessionState.Error -> "Could not connect"
                            else -> "No account connected"
                        }, style = MaterialTheme.typography.titleMedium)
                        if (session is MobileSessionState.Error) Text(session.reason.message())
                        Text("Start pairing, then approve the code in your signed-in LeakTrace web dashboard.")
                        Button(onClick = onStartPairing) { Text("Pair this device") }
                        if (session is MobileSessionState.Error) OutlinedButton(onClick = onRetry) { Text("Try again") }
                    }
                }
            }
        }
        Text("Account data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(if (session is MobileSessionState.Connected) "Websites, email activity and timeline load from your account on this server." else "No account records are loaded until this device is paired.")
    }
}
