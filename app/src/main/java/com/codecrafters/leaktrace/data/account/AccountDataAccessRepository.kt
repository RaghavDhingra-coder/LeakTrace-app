package com.codecrafters.leaktrace.data.account

import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.data.server.ServerReport

enum class AccountDataSection(val feature: BackendFeature) {
    Websites(BackendFeature.Websites),
    Email(BackendFeature.Email),
    Timeline(BackendFeature.Timeline),
}

/** Access status from unauthenticated route probes; typed account reads use MobileRepository. */
sealed interface AccountDataAccess {
    data object Loading : AccountDataAccess
    data object AuthenticationRequired : AccountDataAccess
    data object LocalOnly : AccountDataAccess
    data object NotDeployed : AccountDataAccess
    data object NetworkError : AccountDataAccess
    data class UnexpectedResponse(val httpCode: Int?) : AccountDataAccess
    /** An unexpected successful unauthenticated response is not private account data. */
    data object DataContractMissing : AccountDataAccess
}

/** Maps the existing unauthenticated server report; does not fetch private account data. */
interface AccountDataAccessRepository {
    fun status(section: AccountDataSection, report: ServerReport?): AccountDataAccess
}

object ProbedAccountDataAccessRepository : AccountDataAccessRepository {
    override fun status(section: AccountDataSection, report: ServerReport?): AccountDataAccess {
        if (report == null) return AccountDataAccess.Loading
        when (val health = report.health) {
            ServerHealth.Unreachable -> return AccountDataAccess.NetworkError
            is ServerHealth.Unexpected -> return AccountDataAccess.UnexpectedResponse(health.httpCode)
            is ServerHealth.Online -> Unit
        }
        return when (val availability = report.features[section.feature]) {
            FeatureAvailability.RequiresSignIn -> AccountDataAccess.AuthenticationRequired
            FeatureAvailability.LocalOnly -> AccountDataAccess.LocalOnly
            FeatureAvailability.NotDeployed -> AccountDataAccess.NotDeployed
            is FeatureAvailability.Unknown -> when (availability.httpCode) {
                null -> AccountDataAccess.NetworkError
                in 200..299 -> AccountDataAccess.DataContractMissing
                else -> AccountDataAccess.UnexpectedResponse(availability.httpCode)
            }
            null -> AccountDataAccess.UnexpectedResponse(null)
        }
    }
}
