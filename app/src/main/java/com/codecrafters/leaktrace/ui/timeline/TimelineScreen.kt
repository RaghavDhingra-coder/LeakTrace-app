package com.codecrafters.leaktrace.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.account.AccountDataSection
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.remote.TimelineDto
import com.codecrafters.leaktrace.ui.components.EvidenceNotice
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
fun TimelineScreen(serverState: ServerUiState, data: MobileLoadState<TimelineDto> = MobileLoadState.NeedsAccount, onRetry: () -> Unit, onOpenAccount: () -> Unit = {}, modifier: Modifier = Modifier) {
    if (showMobileData(data, serverState.availability(BackendFeature.Timeline))) {
        MobileDataScreen(stringResource(R.string.desc_timeline), data, onRetry, onOpenAccount, modifier) { timeline ->
            Text(timeline.notice, style = MaterialTheme.typography.bodySmall)
            timeline.items.forEach { event ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Text("${event.channel.uppercase()} · ${event.at}", style = MaterialTheme.typography.bodySmall)
                        event.siteName?.let { Text(it) }
                        event.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
        return
    }
    FeatureScreen(
        feature = BackendFeature.Timeline,
        dataSection = AccountDataSection.Timeline,
        serverState = serverState,
        onRetry = onRetry,
        description = R.string.desc_timeline,
        planned = listOf(R.string.plan_timeline_1, R.string.plan_timeline_2),
        modifier = modifier,
    ) {
        EvidenceNotice()
    }
}
