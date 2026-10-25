package com.codecrafters.leaktrace.ui.settings

import android.text.format.DateUtils
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.BuildConfig
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.ui.server.AvailabilityChip
import com.codecrafters.leaktrace.ui.server.ServerStatusCard
import com.codecrafters.leaktrace.ui.server.ServerUiState
import com.codecrafters.leaktrace.ui.mobile.MobileSessionState

@Composable
fun SettingsScreen(serverState: ServerUiState, session: MobileSessionState, onRetry: () -> Unit, onOpenAccount: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingsSection(R.string.settings_server) {
            LabeledValue(stringResource(R.string.settings_server_address), serverState.baseUrl, monospace = true)
            ServerStatusCard(serverState, onRetry)
            serverState.report?.let { report ->
                Text(
                    text = stringResource(
                        R.string.server_last_checked,
                        DateUtils.getRelativeTimeSpanString(report.checkedAtMillis).toString(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SettingsSection(R.string.settings_server_features) {
            Text(
                text = stringResource(R.string.settings_server_features_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                BackendFeature.entries.forEachIndexed { index, feature ->
                    if (index > 0) HorizontalDivider()
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(feature.label), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = feature.probePath,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AvailabilityChip(serverState.availability(feature))
                    }
                }
            }
        }

        SettingsSection(R.string.settings_account) {
            Text(if (session is MobileSessionState.Connected) "Connected as ${session.account.email}. Manage this device in Account." else "Pair this device from your LeakTrace web dashboard when the mobile API is available.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenAccount) { Text(stringResource(R.string.settings_account_details)) }
        }

        SettingsSection(R.string.settings_privacy) {
            Text("LeakTrace does not read your SMS inbox, contacts, or email on this device. Server checks and paired account requests use the configured backend. SMS text is sent only if you explicitly choose server analysis.", style = MaterialTheme.typography.bodyMedium)
        }

        SettingsSection(R.string.settings_about) {
            LabeledValue(stringResource(R.string.app_name), stringResource(R.string.home_build, BuildConfig.VERSION_NAME))
        }
    }
}

private val BackendFeature.label: Int
    @StringRes get() = when (this) {
        BackendFeature.Account -> R.string.feature_account
        BackendFeature.Websites -> R.string.title_websites
        BackendFeature.Email -> R.string.title_email
        BackendFeature.Sms -> R.string.title_sms
        BackendFeature.Timeline -> R.string.title_timeline
    }

@Composable
private fun SettingsSection(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun LabeledValue(label: String, value: String, monospace: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (monospace) FontFamily.Monospace else null,
        )
    }
}
