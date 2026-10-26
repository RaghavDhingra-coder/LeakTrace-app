package com.codecrafters.leaktrace.ui.email

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.account.AccountDataSection
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.remote.EmailPageDto
import com.codecrafters.leaktrace.data.remote.attributionLabel
import com.codecrafters.leaktrace.ui.components.ExposureCard
import com.codecrafters.leaktrace.ui.components.FeatureScreen
import com.codecrafters.leaktrace.ui.components.MobileDataScreen
import com.codecrafters.leaktrace.ui.components.showMobileData
import com.codecrafters.leaktrace.ui.mobile.MobileLoadState
import com.codecrafters.leaktrace.ui.server.ServerUiState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun EmailActivityScreen(serverState: ServerUiState, data: MobileLoadState<EmailPageDto> = MobileLoadState.NeedsAccount, onRetry: () -> Unit, onOpenAccount: () -> Unit = {}, modifier: Modifier = Modifier) {
    if (showMobileData(data, serverState.availability(BackendFeature.Email))) {
        MobileDataScreen(stringResource(R.string.desc_email), data, onRetry, onOpenAccount, modifier) { page ->
            page.items.forEach { email ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(email.siteName ?: "Email without website attribution", style = MaterialTheme.typography.titleMedium)
                        email.alias?.let { Text("Recipient alias: $it") }
                        if (email.unread) Text("Unread")
                        Text(email.receivedAt, style = MaterialTheme.typography.bodySmall)
                        email.senderDomain?.let { Text("Claimed sender domain: $it", style = MaterialTheme.typography.bodySmall) }
                        Text("Recipient status: ${email.attributionLabel()}", style = MaterialTheme.typography.bodyMedium)
                        email.exposure.forEach { ExposureCard(it) }
                    }
                }
            }
            Text("Matched identifies the recipient alias’s registered website. It does not verify the sender or mean the message is safe.")
            if (page.total > page.items.size) Text("Showing ${page.items.size} of ${page.total} emails.")
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
