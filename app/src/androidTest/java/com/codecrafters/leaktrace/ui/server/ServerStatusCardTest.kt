package com.codecrafters.leaktrace.ui.server

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.data.server.ServerReport
import com.codecrafters.leaktrace.ui.theme.LeakTraceTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ServerStatusCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unreachableServerShowsRetryAndInvokesIt() {
        var retries = 0
        val state = ServerUiState(
            baseUrl = "https://leaktrace.test",
            report = ServerReport("https://leaktrace.test", ServerHealth.Unreachable, emptyMap(), 0L),
        )
        compose.setContent { LeakTraceTheme { ServerStatusCard(state, onRetry = { retries++ }) } }

        compose.onNodeWithText("Can't reach the LeakTrace server").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)
    }

    @Test fun initialCheckHasNoRetryUntilItFinishes() {
        compose.setContent {
            LeakTraceTheme {
                ServerStatusCard(ServerUiState(baseUrl = "https://leaktrace.test", isChecking = true), onRetry = {})
            }
        }

        compose.onNodeWithText("Checking the LeakTrace server…").assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }
}
