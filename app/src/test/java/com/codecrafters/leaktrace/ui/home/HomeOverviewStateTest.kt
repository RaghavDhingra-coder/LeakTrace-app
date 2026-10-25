package com.codecrafters.leaktrace.ui.home

import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.data.server.ServerReport
import com.codecrafters.leaktrace.ui.server.ServerUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeOverviewStateTest {
    private fun state(health: ServerHealth) = ServerUiState(
        baseUrl = "https://leaktrace.test",
        report = ServerReport("https://leaktrace.test", health, emptyMap(), 0L),
    )

    @Test fun loadingUntilFirstCheckCompletes() {
        assertEquals(HomeOverviewState.Loading, ServerUiState("https://leaktrace.test", isChecking = true).homeOverviewState())
    }

    @Test fun onlineServerStillHasNoAccountActivity() {
        assertEquals(HomeOverviewState.Empty, state(ServerHealth.Online("1.0")).homeOverviewState())
    }

    @Test fun unreachableOrUnexpectedServerShowsError() {
        assertEquals(HomeOverviewState.Error, state(ServerHealth.Unreachable).homeOverviewState())
        assertEquals(HomeOverviewState.Error, state(ServerHealth.Unexpected(503)).homeOverviewState())
    }
}
