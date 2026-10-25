package com.codecrafters.leaktrace.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.ui.navigation.LeakTraceDestination
import com.codecrafters.leaktrace.ui.mobile.MobileSessionState
import com.codecrafters.leaktrace.ui.server.AvailabilityChip
import com.codecrafters.leaktrace.ui.server.ServerStatusCard
import com.codecrafters.leaktrace.ui.server.ServerUiState

private val sections = listOf(
    LeakTraceDestination.Websites to R.string.desc_websites,
    LeakTraceDestination.Email to R.string.desc_email,
    LeakTraceDestination.Sms to R.string.desc_sms,
    LeakTraceDestination.Timeline to R.string.desc_timeline,
)

@Composable
fun HomeScreen(
    serverState: ServerUiState,
    session: MobileSessionState,
    onRetry: () -> Unit,
    onOpenSection: (LeakTraceDestination) -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader()
        Text(
            stringResource(R.string.home_connection_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        ServerStatusCard(serverState, onRetry)
        if (session is MobileSessionState.Connected) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Account activity", style = MaterialTheme.typography.titleMedium)
                    Text("Your paired account is connected. Open Websites, Email or Timeline to load and refresh your records.")
                }
            }
        } else OverviewCard(serverState.homeOverviewState())
        AccountCard(serverState.availability(BackendFeature.Account), serverState.homeOverviewState(), session, onOpenAccount)
        Text(
            text = stringResource(R.string.home_sections_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.home_sections_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        sections.forEach { (destination, description) ->
            SectionCard(
                destination = destination,
                description = description,
                availability = destination.feature?.let(serverState::availability),
                connected = session is MobileSessionState.Connected,
                onClick = { onOpenSection(destination) },
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.home_eyebrow), style = MaterialTheme.typography.labelLarge)
            Text(
                stringResource(R.string.home_welcome),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.home_welcome_body), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun AccountCard(availability: FeatureAvailability?, state: HomeOverviewState, session: MobileSessionState, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Person, contentDescription = null)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (session is MobileSessionState.Connected) "Account connected" else stringResource(R.string.home_account_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = if (session is MobileSessionState.Connected) session.account.email else stringResource(
                        when {
                            state == HomeOverviewState.Loading -> R.string.home_account_checking
                            state == HomeOverviewState.Error -> R.string.home_account_status_unknown
                            availability == FeatureAvailability.RequiresSignIn -> R.string.home_account_pairing_available
                            availability == FeatureAvailability.LocalOnly -> R.string.home_account_local_only
                            availability is FeatureAvailability.Unknown -> R.string.home_account_status_unknown
                            else -> R.string.home_account_unavailable
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (session !is MobileSessionState.Connected) AvailabilityChip(availability)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SectionCard(
    destination: LeakTraceDestination,
    description: Int,
    availability: FeatureAvailability?,
    connected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(destination.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(destination.title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (destination == LeakTraceDestination.Sms) {
                    Text(
                        stringResource(R.string.home_sms_available_action),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    if (connected && availability == FeatureAvailability.RequiresSignIn) {
                        Text("Open account data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    } else AvailabilityChip(availability)
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OverviewCard(state: HomeOverviewState) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (state == HomeOverviewState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(when (state) {
                        HomeOverviewState.Loading -> R.string.home_overview_loading_title
                        HomeOverviewState.Empty -> R.string.home_overview_empty_title
                        HomeOverviewState.Error -> R.string.home_overview_error_title
                    }),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(when (state) {
                        HomeOverviewState.Loading -> R.string.home_overview_loading_body
                        HomeOverviewState.Empty -> R.string.home_overview_empty_body
                        HomeOverviewState.Error -> R.string.home_overview_error_body
                    }),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
