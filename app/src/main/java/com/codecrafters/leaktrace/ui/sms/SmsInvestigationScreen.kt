package com.codecrafters.leaktrace.ui.sms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codecrafters.leaktrace.R
import com.codecrafters.leaktrace.ui.sms.SmsDraftState.Companion.MAX_SMS_LENGTH
import com.codecrafters.leaktrace.data.remote.SmsAnalysisDto
import com.codecrafters.leaktrace.data.remote.WebsitePageDto
import com.codecrafters.leaktrace.ui.mobile.MobileLoadState
import com.codecrafters.leaktrace.ui.mobile.MobileSessionState
import com.codecrafters.leaktrace.ui.components.EmptyState
import com.codecrafters.leaktrace.ui.components.ExposureCard
import com.codecrafters.leaktrace.ui.components.message

@Composable
fun SmsInvestigationScreen(
    viewModel: SmsInvestigationViewModel,
    session: MobileSessionState,
    websites: MobileLoadState<WebsitePageDto>,
    serverAnalysisAvailable: Boolean,
    remote: MobileLoadState<SmsAnalysisDto>,
    onAnalyzeRemote: (String, String?) -> Unit,
    onClearRemote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val analysis by viewModel.analysis.collectAsStateWithLifecycle()
    var showInbox by remember { mutableStateOf(false) }
    var sender by remember { mutableStateOf("") }
    val ownedWebsites = if (session is MobileSessionState.Connected) when (websites) {
        is MobileLoadState.Ready -> websites.value
        MobileLoadState.Empty -> WebsitePageDto(emptyList(), 0, 50, 0)
        else -> null
    } else null
    LaunchedEffect(session, websites) { viewModel.updateAccountComparison(ownedWebsites) }
    if (showInbox) {
        SmsInboxScreen(onBack = { showInbox = false }, serverAnalysisAvailable = serverAnalysisAvailable, onCheck = { message ->
            val selected = selectedSmsCheck(message, serverAnalysisAvailable)
            sender = selected.sender.orEmpty()
            viewModel.onTextChange(selected.body)
            viewModel.analyze(ownedWebsites)
            onClearRemote()
            if (selected.sendToServer) onAnalyzeRemote(selected.body, selected.sender)
            showInbox = false
        })
        return
    }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.desc_sms), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(onClick = { showInbox = true }) { Text("Device SMS inbox") }
        SmsInput(draft, analysis, { viewModel.onTextChange(it); onClearRemote() }, { viewModel.analyze(ownedWebsites) }, { viewModel.clear(); onClearRemote() })
        SmsResults(analysis, ownedWebsites)
        if (session is MobileSessionState.Connected && serverAnalysisAvailable) {
            Text("Optional account comparison", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("This sends the message and optional sender to your paired LeakTrace server to compare with your account records. It may suggest sources; it cannot verify a sender or prove a leak.")
            OutlinedTextField(value = sender, onValueChange = { sender = it.take(64); onClearRemote() }, label = { Text("Sender number or name (optional)") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onAnalyzeRemote(draft.text, sender.trim().ifEmpty { null }) }, enabled = !draft.isBlank && !draft.isTooLong && remote != MobileLoadState.Loading) { Text("Send to server for comparison") }
            when (remote) {
                MobileLoadState.Loading -> Text("Comparing with account records…")
                is MobileLoadState.Ready -> RemoteSmsResults(remote.value)
                is MobileLoadState.Error -> {
                    EmptyState("Server analysis failed", remote.reason.message())
                    OutlinedButton(onClick = { onAnalyzeRemote(draft.text, sender.trim().ifEmpty { null }) }) { Text("Try again") }
                }
                MobileLoadState.Unavailable -> {
                    EmptyState("Server analysis unavailable", "The configured server does not provide this mobile route.")
                    OutlinedButton(onClick = { onAnalyzeRemote(draft.text, sender.trim().ifEmpty { null }) }) { Text("Try again") }
                }
                MobileLoadState.SessionExpired -> EmptyState("Session expired", "Sign in or pair this device again to use server comparison.")
                else -> Unit
            }
        }
        Text(
            stringResource(R.string.sms_heuristic_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (session is MobileSessionState.Connected && serverAnalysisAvailable) "Local analysis stays on this device. The server receives message text only when you tap Send to server for comparison." else stringResource(R.string.sms_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RemoteSmsResults(result: SmsAnalysisDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Server comparison", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        result.risk?.let { risk ->
            if (risk.exposure.isNotEmpty()) {
                Text("Where your detail may have come from", style = MaterialTheme.typography.titleMedium)
                risk.exposure.forEach { ExposureCard(it) }
            }
            Text("${risk.assessment} · ${risk.riskLevel}", style = MaterialTheme.typography.titleMedium)
            risk.reasons.forEach { Text("• ${it.message}${it.evidence?.let { e -> " ($e)" } ?: ""}") }
            risk.domains.forEach { Text("Domain: $it") }
            risk.nextSteps.forEach { Text(it) }
            Text(risk.notice)
        } ?: Text("This server does not yet return a scam assessment. Source suggestions are not a safety verdict.")
        Text(result.notice, style = MaterialTheme.typography.bodySmall)
        result.sender?.let { Text("Sender supplied: ${it.display} (${it.type}); identity not verified") }
        result.senderNotes.forEach { Text(it) }
        if (result.entities.isNotEmpty()) {
            Text("Details recognized by the server", style = MaterialTheme.typography.titleSmall)
            result.entities.filterNot { it.excluded }.forEach { entity ->
                Text("${entity.type}: ${entity.display}${entity.relation?.let { " · $it" } ?: ""}")
            }
        }
        if (result.candidates.isEmpty()) EmptyState("No source suggested", result.missing.joinToString(" ").ifBlank { "The available evidence did not identify a source." })
        result.candidates.forEach { candidate ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                    Text("Status: ${candidate.status}")
                    candidate.signals.forEach { Text("• ${it.explanation}") }
                    candidate.unknowns.forEach { Text("Unknown: $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        result.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        result.limitations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SmsInput(
    draft: SmsDraftState,
    analysis: SmsAnalysisState,
    onTextChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = draft.text,
            onValueChange = onTextChange,
            label = { Text(stringResource(R.string.sms_input_label)) },
            placeholder = { Text(stringResource(R.string.sms_input_hint)) },
            isError = draft.isTooLong || analysis == SmsAnalysisState.EmptyInput,
            supportingText = {
                Text(
                    if (draft.isTooLong) stringResource(R.string.sms_too_long, MAX_SMS_LENGTH)
                    else if (analysis == SmsAnalysisState.EmptyInput) stringResource(R.string.sms_empty_input)
                    else stringResource(R.string.sms_counter, draft.length, MAX_SMS_LENGTH),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAnalyze) { Text(stringResource(R.string.sms_analyze)) }
            OutlinedButton(onClick = onClear, enabled = draft.text.isNotEmpty()) {
                Text(stringResource(R.string.sms_clear))
            }
        }
    }
}

@Composable
private fun SmsResults(state: SmsAnalysisState, ownedWebsites: WebsitePageDto?) {
    when (state) {
        SmsAnalysisState.NotAnalyzed -> ResultNotice(R.string.sms_ready_title, R.string.sms_ready_body)
        SmsAnalysisState.EmptyInput -> ResultNotice(R.string.sms_invalid_title, R.string.sms_empty_input)
        SmsAnalysisState.TooLong -> ResultNotice(R.string.sms_invalid_title, R.string.sms_too_long, MAX_SMS_LENGTH)
        is SmsAnalysisState.Complete -> {
            val result = state.analysis
            val accountDataVisible = ownedWebsites != null
            val currentSources = result.possibleSources.filter { clue ->
                ownedWebsites?.items?.any { it.id == clue.signupId && it.nickname == clue.marker } == true
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.sms_results_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    stringResource(R.string.sms_results_summary, result.urls.size, result.indicators.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Possible source clues", style = MaterialTheme.typography.titleMedium)
                currentSources.forEach { ExposureCard(it) }
                if (currentSources.isEmpty()) {
                    Text(if (accountDataVisible) "No registered nickname matched this message." else "Sign in and load your websites to compare registered nicknames on this device.",
                        style = MaterialTheme.typography.bodySmall)
                }
                if (accountDataVisible && result.accountComparisonAvailable && !result.accountComparisonComplete) {
                    Text("Only loaded websites were checked. Refresh your websites for complete coverage.", style = MaterialTheme.typography.bodySmall)
                }
                ResultListTitle(R.string.sms_links_title)
                if (result.urls.isEmpty()) {
                    ResultNotice(R.string.sms_no_links_title, R.string.sms_no_links_body)
                } else {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            result.urls.forEach { url ->
                                SelectionContainer { Text(url, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                    }
                }
                ResultListTitle(R.string.sms_indicators_title)
                if (result.indicators.isEmpty()) {
                    ResultNotice(R.string.sms_no_indicators_title, R.string.sms_no_indicators_body)
                } else {
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        result.indicators.forEachIndexed { index, indicator ->
                            if (index > 0) HorizontalDivider()
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(stringResource(indicator.title), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(indicator.explanation),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultListTitle(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
}

@Composable
private fun ResultNotice(title: Int, body: Int, vararg args: Any) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(body, *args), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private val SmsIndicator.title: Int get() = when (this) {
    SmsIndicator.Urgency -> R.string.sms_urgency_title
    SmsIndicator.AccountThreat -> R.string.sms_account_threat_title
    SmsIndicator.SensitiveInfoRequest -> R.string.sms_sensitive_request_title
    SmsIndicator.PaymentOrPrize -> R.string.sms_payment_prize_title
    SmsIndicator.InsecureLink -> R.string.sms_insecure_link_title
    SmsIndicator.ShortenedLink -> R.string.sms_short_link_title
    SmsIndicator.IpAddressLink -> R.string.sms_ip_link_title
    SmsIndicator.MisleadingLink -> R.string.sms_misleading_link_title
}

private val SmsIndicator.explanation: Int get() = when (this) {
    SmsIndicator.Urgency -> R.string.sms_urgency_body
    SmsIndicator.AccountThreat -> R.string.sms_account_threat_body
    SmsIndicator.SensitiveInfoRequest -> R.string.sms_sensitive_request_body
    SmsIndicator.PaymentOrPrize -> R.string.sms_payment_prize_body
    SmsIndicator.InsecureLink -> R.string.sms_insecure_link_body
    SmsIndicator.ShortenedLink -> R.string.sms_short_link_body
    SmsIndicator.IpAddressLink -> R.string.sms_ip_link_body
    SmsIndicator.MisleadingLink -> R.string.sms_misleading_link_body
}
