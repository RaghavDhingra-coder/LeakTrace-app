package com.codecrafters.leaktrace.ui.email

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.account.AccountDataSection
import com.codecrafters.leaktrace.data.remote.EmailPageDto
import com.codecrafters.leaktrace.data.remote.attributionLabel
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.ui.components.ExposureCard
import com.codecrafters.leaktrace.ui.components.FeatureScreen
import com.codecrafters.leaktrace.ui.components.MobileDataScreen
import com.codecrafters.leaktrace.ui.components.showMobileData
import com.codecrafters.leaktrace.ui.mobile.MobileFailure
import com.codecrafters.leaktrace.ui.mobile.MobileLoadState
import com.codecrafters.leaktrace.ui.server.ServerUiState

@Composable
fun EmailActivityScreen(
    serverState: ServerUiState,
    data: MobileLoadState<EmailPageDto> = MobileLoadState.NeedsAccount,
    snapshot: EmailPageDto? = null,
    onRetry: () -> Unit,
    onOpenAccount: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val canShowSnapshot = data == MobileLoadState.Loading ||
        (data is MobileLoadState.Error && data.reason in setOf(MobileFailure.Network, MobileFailure.Server, MobileFailure.RateLimited))
    val shown: MobileLoadState<EmailPageDto> = if (canShowSnapshot && snapshot != null) MobileLoadState.Ready(snapshot) else data
    if (showMobileData(shown, serverState.availability(BackendFeature.Email))) {
        MobileDataScreen(stringResource(R.string.desc_email), shown, onRetry, onOpenAccount, modifier) { page ->
            if (shown != data) {
                Text(if (data == MobileLoadState.Loading) "Refreshing email activity… showing last synced records."
                    else "Could not refresh. Showing last synced records; new emails may be missing.",
                    color = MaterialTheme.colorScheme.error)
            }
            Button(onClick = onRetry) { Text("Refresh email activity") }
            if (page.items.isEmpty()) Text("No emails received at your aliases yet.")
            page.items.forEach { email ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(if (email.tracked) email.subject ?: "(no subject)" else "Recorded before inbox tracking",
                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("Received: ${email.receivedAt}", style = MaterialTheme.typography.bodySmall)
                        if (email.unread) Text("Unread")
                        Text("Recipient alias: ${email.alias ?: "Not recorded"}")
                        Text("Alias website: ${email.siteName ?: "No registered website association"}")
                        Text("Recipient attribution: ${email.attributionLabel()}")
                        email.statusReason?.let { Text("Attribution detail: $it", style = MaterialTheme.typography.bodySmall) }
                        if (email.tracked) {
                            Text("Claimed sender: ${email.senderEmail ?: "Unavailable"}")
                            email.senderName?.let { Text("Claimed display name: $it", style = MaterialTheme.typography.bodySmall) }
                            Text("Claimed sender domain: ${email.senderDomain ?: "Unavailable"}", style = MaterialTheme.typography.bodySmall)
                            Text("Provider-reported sender authentication: ${email.authResults ?: "Not available"}",
                                style = MaterialTheme.typography.bodySmall)
                        } else Text("Sender and subject were not stored for this historical record.", style = MaterialTheme.typography.bodySmall)
                        if (email.exposure.isNotEmpty()) {
                            Text("Possible source leads", style = MaterialTheme.typography.titleSmall)
                            email.exposure.forEach { ExposureCard(it) }
                        } else Text("No possible source finding from the available evidence.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("A matched alias identifies its registered website. It does not verify the sender, establish safety, or prove a data leak.",
                style = MaterialTheme.typography.bodySmall)
            if (page.total > page.items.size) Text("Showing ${page.items.size} of ${page.total} emails. Refresh to retry incomplete paging.")
        }
        return
    }
    FeatureScreen(
        feature = BackendFeature.Email,
        dataSection = AccountDataSection.Email,
        serverState = serverState,
        onRetry = onRetry,
        description = R.string.desc_email,
        planned = listOf(R.string.plan_email_1, R.string.plan_email_2, R.string.plan_email_3),
        modifier = modifier,
    )
}
