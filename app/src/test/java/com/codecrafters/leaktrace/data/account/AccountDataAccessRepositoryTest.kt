package com.codecrafters.leaktrace.data.account

import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.data.server.ServerReport
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDataAccessRepositoryTest {
    private fun report(section: AccountDataSection, availability: FeatureAvailability, health: ServerHealth = ServerHealth.Online("1")) =
        ServerReport("https://leaktrace.test", health, mapOf(section.feature to availability), 0L)

    @Test fun everyAccountSectionUsesItsOwnProbe() {
        AccountDataSection.entries.forEach { section ->
            val result = ProbedAccountDataAccessRepository.status(
                section, report(section, FeatureAvailability.RequiresSignIn),
            )
            assertEquals(AccountDataAccess.AuthenticationRequired, result)
        }
    }

    @Test fun routeRestrictionsAreNotTreatedAsEmptyRecords() {
        assertEquals(AccountDataAccess.Loading, ProbedAccountDataAccessRepository.status(AccountDataSection.Websites, null))
        assertEquals(AccountDataAccess.LocalOnly, ProbedAccountDataAccessRepository.status(
            AccountDataSection.Websites, report(AccountDataSection.Websites, FeatureAvailability.LocalOnly)))
        assertEquals(AccountDataAccess.NotDeployed, ProbedAccountDataAccessRepository.status(
            AccountDataSection.Email, report(AccountDataSection.Email, FeatureAvailability.NotDeployed)))
    }

    @Test fun transportAndUnexpectedResponsesStayDistinct() {
        val section = AccountDataSection.Timeline
        assertEquals(AccountDataAccess.NetworkError, ProbedAccountDataAccessRepository.status(
            section, report(section, FeatureAvailability.Unknown(null))))
        assertEquals(AccountDataAccess.UnexpectedResponse(503), ProbedAccountDataAccessRepository.status(
            section, report(section, FeatureAvailability.Unknown(503))))
        assertEquals(AccountDataAccess.NetworkError, ProbedAccountDataAccessRepository.status(
            section, report(section, FeatureAvailability.RequiresSignIn, ServerHealth.Unreachable)))
        assertEquals(AccountDataAccess.UnexpectedResponse(502), ProbedAccountDataAccessRepository.status(
            section, report(section, FeatureAvailability.RequiresSignIn, ServerHealth.Unexpected(502))))
    }

    @Test fun successfulProbeDoesNotBecomeFabricatedData() {
        val section = AccountDataSection.Websites
        assertEquals(AccountDataAccess.DataContractMissing, ProbedAccountDataAccessRepository.status(
            section, report(section, FeatureAvailability.Unknown(200))))
    }
}
