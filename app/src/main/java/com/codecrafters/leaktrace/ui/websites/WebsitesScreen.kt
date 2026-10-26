package com.codecrafters.leaktrace.ui.websites

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.account.AccountDataSection
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.remote.WebsitePageDto
import com.codecrafters.leaktrace.ui.components.FeatureScreen
import com.codecrafters.leaktrace.ui.components.MobileDataScreen
import com.codecrafters.leaktrace.ui.components.showMobileData
import com.codecrafters.leaktrace.ui.mobile.MobileLoadState
import com.codecrafters.leaktrace.ui.server.ServerUiState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun WebsitesScreen(serverState: ServerUiState, data: MobileLoadState<WebsitePageDto> = MobileLoadState.NeedsAccount, snapshot: WebsitePageDto? = null, onRetry: () -> Unit, onOpenAccount: () -> Unit = {}, modifier: Modifier = Modifier) {
    val canShowSnapshot = data == MobileLoadState.Loading ||
        (data is MobileLoadState.Error && data.reason in setOf(com.codecrafters.leaktrace.ui.mobile.MobileFailure.Network,
            com.codecrafters.leaktrace.ui.mobile.MobileFailure.Server, com.codecrafters.leaktrace.ui.mobile.MobileFailure.RateLimited))
    val shown: MobileLoadState<WebsitePageDto> = if (canShowSnapshot && snapshot != null) MobileLoadState.Ready(snapshot) else data
    if (showMobileData(shown, serverState.availability(BackendFeature.Websites))) {
        MobileDataScreen(stringResource(R.string.desc_websites), shown, onRetry, onOpenAccount, modifier) { page ->
            if (shown != data) Text(if (data == MobileLoadState.Loading) "Refreshing websites… showing last synced records."
                else "Could not refresh. Showing last synced records; changes may be missing.", color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Refresh websites and aliases") }
            if (page.items.isEmpty()) Text("No registered websites yet.")
            page.items.forEach { site ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(site.siteName, style = MaterialTheme.typography.titleMedium)
                        Text("Alias: ${site.alias ?: "Not registered"}")
                        site.nickname?.let { Text("Nickname: $it") }
                        site.siteDomain?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text("Emails: ${site.emailCount} · Receipts: ${site.receiptCount}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (page.total > page.items.size) Text("Showing ${page.items.size} of ${page.total} websites. Refresh to retry incomplete paging.")
        }
        return
    }
    FeatureScreen(
        feature = BackendFeature.Websites,
        dataSection = AccountDataSection.Websites,
        serverState = serverState,
        onRetry = onRetry,
        description = R.string.desc_websites,
        planned = listOf(R.string.plan_websites_1, R.string.plan_websites_2, R.string.plan_websites_3),
        modifier = modifier,
    )
}
