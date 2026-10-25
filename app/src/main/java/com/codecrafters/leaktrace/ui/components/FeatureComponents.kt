package com.codecrafters.leaktrace.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.account.AccountDataSection
import com.codecrafters.leaktrace.data.account.ProbedAccountDataAccessRepository
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.ui.server.FeatureStatusCard
import com.codecrafters.leaktrace.ui.server.AccountDataStatusCard
import com.codecrafters.leaktrace.ui.server.ServerUiState

/**
 * Layout shared by the feature sections. The no-data notice means records have not been
 * loaded; it does not claim that the server returned an empty collection. [content] goes
 * between the status and the notice when a section needs additional local content.
 */
@Composable
fun FeatureScreen(
    feature: BackendFeature,
    serverState: ServerUiState,
    onRetry: () -> Unit,
    @StringRes description: Int,
    @StringRes planned: List<Int>,
    modifier: Modifier = Modifier,
    dataSection: AccountDataSection? = null,
    @StringRes emptyTitle: Int = R.string.feature_no_data_title,
    @StringRes emptyMessage: Int = R.string.feature_empty,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val dataState = dataSection?.let {
        AccountDataUiState(ProbedAccountDataAccessRepository.status(it, serverState.report), serverState.isChecking)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(description), style = MaterialTheme.typography.bodyLarge)
        if (dataState != null) {
            AccountDataStatusCard(dataState.access, dataState.isRefreshing, onRetry)
        } else {
            FeatureStatusCard(
                availability = serverState.availability(feature),
                isChecking = serverState.isChecking,
                onRetry = onRetry,
            )
        }
        content()
        if (dataState?.showNoDataNotice == true || (dataSection == null && serverState.report != null)) {
            EmptyState(stringResource(emptyTitle), stringResource(emptyMessage))
        }
        PlannedCard(planned)
    }
}

@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PlannedCard(@StringRes items: List<Int>, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.feature_planned),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            items.forEach { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clearAndSetSemantics { })
                    Text(stringResource(item), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Reminder that source matching is evidence, not proof of a leak. */
@Composable
fun EvidenceNotice(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.evidence_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
