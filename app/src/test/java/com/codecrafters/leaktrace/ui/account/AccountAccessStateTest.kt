package com.codecrafters.leaktrace.ui.account

import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.data.server.ServerReport
import com.codecrafters.leaktrace.ui.server.ServerUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountAccessStateTest {
    private fun state(health: ServerHealth, availability: FeatureAvailability? = null) = ServerUiState(
        baseUrl = "https://leaktrace.test",
        report = ServerReport(
            "https://leaktrace.test",
            health,
            availability?.let { mapOf(BackendFeature.Account to it) }.orEmpty(),
            0L,
        ),
    )

    @Test fun initialCheckIsLoading() {
        assertEquals(AccountAccessState.Checking, ServerUiState("https://leaktrace.test", isChecking = true).accountAccessState())
    }

    @Test fun unauthorizedMobileProbeShowsPairingAvailability() {
        assertEquals(
            AccountAccessState.PairingAvailable,
            state(ServerHealth.Online("1"), FeatureAvailability.RequiresSignIn).accountAccessState(),
        )
    }

    @Test fun routeRestrictionsStayDistinct() {
        assertEquals(AccountAccessState.LocalOnly, state(ServerHealth.Online("1"), FeatureAvailability.LocalOnly).accountAccessState())
        assertEquals(AccountAccessState.NotDeployed, state(ServerHealth.Online("1"), FeatureAvailability.NotDeployed).accountAccessState())
    }

    @Test fun networkOrUnknownResponseIsUnavailable() {
        assertEquals(AccountAccessState.Unavailable, state(ServerHealth.Unreachable).accountAccessState())
        assertEquals(AccountAccessState.Unavailable, state(ServerHealth.Online("1"), FeatureAvailability.Unknown(null)).accountAccessState())
    }
}
