package com.codecrafters.leaktrace.ui.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.account.AccountDataAccess
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerHealth

/** Liveness of the configured LeakTrace server, with a retry action. */
@Composable
fun ServerStatusCard(state: ServerUiState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val health = state.report?.health
    when {
        health == null -> StatusCard(
            icon = null, title = stringResource(R.string.server_checking), body = null,
            tone = StatusTone.Neutral, isBusy = true, onRetry = null, modifier = modifier,
        )
        health is ServerHealth.Online -> StatusCard(
            icon = Icons.Filled.CheckCircle, title = stringResource(R.string.server_online, health.version),
            body = stringResource(R.string.server_online_body), tone = StatusTone.Good,
            isBusy = state.isChecking, onRetry = onRetry, modifier = modifier,
        )
        health is ServerHealth.Unexpected -> StatusCard(
            icon = Icons.Filled.Warning, title = stringResource(R.string.server_unexpected, health.httpCode),
            body = stringResource(R.string.server_unexpected_body), tone = StatusTone.Error,
            isBusy = state.isChecking, onRetry = onRetry, modifier = modifier,
        )
        else -> StatusCard(
            icon = Icons.Filled.Warning, title = stringResource(R.string.server_unreachable),
            body = stringResource(R.string.server_unreachable_body), tone = StatusTone.Error,
            isBusy = state.isChecking, onRetry = onRetry, modifier = modifier,
        )
    }
}

/** Whether a feature's data can be loaded, and why not. Null [availability] means still checking. */
@Composable
fun FeatureStatusCard(
    availability: FeatureAvailability?,
    isChecking: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (availability) {
        null -> StatusCard(
            icon = null, title = stringResource(R.string.server_checking), body = null,
            tone = StatusTone.Neutral, isBusy = true, onRetry = null, modifier = modifier,
        )
        FeatureAvailability.NotDeployed -> StatusCard(
            icon = Icons.Filled.Info, title = stringResource(R.string.feature_not_deployed_title),
            body = stringResource(R.string.feature_not_deployed_body), tone = StatusTone.Notice,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        FeatureAvailability.LocalOnly -> StatusCard(
            icon = Icons.Filled.Lock, title = stringResource(R.string.feature_local_only_title),
            body = stringResource(R.string.feature_local_only_body), tone = StatusTone.Notice,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        FeatureAvailability.RequiresSignIn -> StatusCard(
            icon = Icons.Filled.Lock, title = stringResource(R.string.feature_requires_sign_in_title),
            body = stringResource(R.string.feature_requires_sign_in_body), tone = StatusTone.Notice,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        is FeatureAvailability.Unknown -> StatusCard(
            icon = Icons.Filled.Warning, title = stringResource(R.string.feature_unknown_title),
            body = availability.httpCode?.let { stringResource(R.string.feature_unknown_body_code, it) }
                ?: stringResource(R.string.feature_unknown_body),
            tone = StatusTone.Error, isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
    }
}

/** The three account-data screens share these states, derived from route probes only. */
@Composable
fun AccountDataStatusCard(
    access: AccountDataAccess,
    isChecking: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (access) {
        AccountDataAccess.Loading -> StatusCard(
            icon = null, title = stringResource(R.string.server_checking), body = null,
            tone = StatusTone.Neutral, isBusy = true, onRetry = null, modifier = modifier,
        )
        AccountDataAccess.AuthenticationRequired -> StatusCard(
            icon = Icons.Filled.Lock, title = stringResource(R.string.feature_requires_sign_in_title),
            body = stringResource(R.string.feature_requires_sign_in_body), tone = StatusTone.Notice,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        AccountDataAccess.LocalOnly -> StatusCard(
            icon = Icons.Filled.Lock, title = stringResource(R.string.feature_local_only_title),
            body = stringResource(R.string.feature_local_only_body), tone = StatusTone.Notice,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        AccountDataAccess.NotDeployed -> StatusCard(
            icon = Icons.Filled.Info, title = stringResource(R.string.feature_not_deployed_title),
            body = stringResource(R.string.feature_not_deployed_body), tone = StatusTone.Notice,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        AccountDataAccess.NetworkError -> StatusCard(
            icon = Icons.Filled.Warning, title = stringResource(R.string.feature_network_error_title),
            body = stringResource(R.string.feature_network_error_body), tone = StatusTone.Error,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        is AccountDataAccess.UnexpectedResponse -> StatusCard(
            icon = Icons.Filled.Warning, title = stringResource(R.string.feature_unknown_title),
            body = access.httpCode?.let { stringResource(R.string.feature_unknown_body_code, it) }
                ?: stringResource(R.string.feature_missing_status_body),
            tone = StatusTone.Error, isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
        AccountDataAccess.DataContractMissing -> StatusCard(
            icon = Icons.Filled.Info, title = stringResource(R.string.feature_contract_missing_title),
            body = stringResource(R.string.feature_contract_missing_body), tone = StatusTone.Notice,
            isBusy = isChecking, onRetry = onRetry, modifier = modifier,
        )
    }
}

@Composable
fun availabilityLabel(availability: FeatureAvailability?): String = stringResource(
    when (availability) {
        null -> R.string.availability_checking
        FeatureAvailability.RequiresSignIn -> R.string.availability_requires_sign_in
        FeatureAvailability.LocalOnly -> R.string.availability_local_only
        FeatureAvailability.NotDeployed -> R.string.availability_not_deployed
        is FeatureAvailability.Unknown -> R.string.availability_unknown
    },
)

/** Small pill showing a feature's availability. */
@Composable
fun AvailabilityChip(availability: FeatureAvailability?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = availabilityLabel(availability),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private enum class StatusTone { Neutral, Good, Notice, Error }

@Composable
private fun StatusCard(
    icon: ImageVector?,
    title: String,
    body: String?,
    tone: StatusTone,
    isBusy: Boolean,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (tone) {
        StatusTone.Neutral -> scheme.surfaceVariant to scheme.onSurfaceVariant
        StatusTone.Good -> scheme.primaryContainer to scheme.onPrimaryContainer
        StatusTone.Notice -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StatusTone.Error -> scheme.errorContainer to scheme.onErrorContainer
    }
    Card(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isBusy && onRetry == null) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = content)
            } else if (icon != null) {
                Icon(icon, contentDescription = null)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium)
                if (onRetry != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onRetry, enabled = !isBusy) {
                            Text(
                                stringResource(if (tone == StatusTone.Error) R.string.action_retry else R.string.server_check_again),
                                color = if (isBusy) Color.Unspecified else content,
                            )
                        }
                        if (isBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = content)
                        }
                    }
                }
            }
        }
    }
}
