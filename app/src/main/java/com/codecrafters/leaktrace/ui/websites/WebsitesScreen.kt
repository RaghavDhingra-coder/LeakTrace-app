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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun WebsitesScreen(serverState: ServerUiState, data: MobileLoadState<WebsitePageDto> = MobileLoadState.NeedsAccount, onRetry: () -> Unit, onOpenAccount: () -> Unit = {}, modifier: Modifier = Modifier) {
    if (showMobileData(data, serverState.availability(BackendFeature.Websites))) {
        MobileDataScreen(stringResource(R.string.desc_websites), data, onRetry, onOpenAccount, modifier) { page ->
            page.items.forEach { site ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(site.siteName, style = MaterialTheme.typography.titleMedium)
                        site.alias?.let { Text("Alias: $it") }
                        site.siteDomain?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Text("Emails: ${site.emailCount} · Receipts: ${site.receiptCount}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (page.total > page.items.size) Text("Showing ${page.items.size} of ${page.total} websites.")
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
