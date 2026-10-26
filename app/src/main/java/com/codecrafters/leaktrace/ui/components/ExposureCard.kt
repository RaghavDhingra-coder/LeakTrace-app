package com.codecrafters.leaktrace.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.codecrafters.leaktrace.data.remote.ExposureDto

/** One user-scoped backend finding. The card never infers a source from UI text. */
@Composable
fun ExposureCard(signal: ExposureDto, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(signal.signupId, signal.markerType) { mutableStateOf(false) }
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = if (expanded) "Hide evidence" else "Show evidence") { expanded = !expanded },
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Column {
                    Text("Possible source: ${signal.siteName}", style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.tertiary)
                    Text(if (signal.markerType == "nickname_mention") "Name clue · tap for evidence" else "Alias clue · tap for evidence",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            AnimatedVisibility(visible = expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(signal.explanation)
                    Text("Your recorded detail: ${signal.marker}")
                    Text("Recorded website: ${signal.siteName}")
                    Text("Expected domain: ${signal.expectedDomain ?: "Not recorded"}")
                    Text("Observed source: ${signal.observedSource ?: "Unverified"}")
                    Text(signal.caveat, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
