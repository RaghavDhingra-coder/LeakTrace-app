package com.codecrafters.leaktrace.ui.websites

import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.data.server.ServerReport
import com.codecrafters.leaktrace.ui.server.ServerUiState
import com.codecrafters.leaktrace.ui.theme.LeakTraceTheme
import org.junit.Rule
import org.junit.Test

class WebsitesScreenStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun checkingDoesNotClaimAnEmptyAccount() {
        compose.setContent {
            LeakTraceTheme {
                WebsitesScreen(ServerUiState("https://leaktrace.test", isChecking = true), onRetry = {})
            }
        }

        compose.onNodeWithText("Checking the LeakTrace server…").assertExists()
        compose.onNodeWithText("No activity loaded").assertDoesNotExist()
    }

    @Test fun authenticationRequiredExplainsWhyNoRecordsAreLoaded() {
        val state = ServerUiState(
            baseUrl = "https://leaktrace.test",
            report = ServerReport(
                "https://leaktrace.test", ServerHealth.Online("1"),
                mapOf(BackendFeature.Websites to FeatureAvailability.RequiresSignIn), 0L,
            ),
        )
        compose.setContent { LeakTraceTheme { WebsitesScreen(state, onRetry = {}) } }

        compose.onNodeWithText("Sign-in needed").assertExists()
        compose.onNodeWithText("No activity loaded").assertExists()
    }
}
