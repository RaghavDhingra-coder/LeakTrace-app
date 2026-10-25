package com.codecrafters.leaktrace.data.server

import com.codecrafters.leaktrace.data.remote.MobilePaths

/** Account and data features of the LeakTrace backend, with the route used to check each one. */
enum class BackendFeature(val probePath: String) {
    // An unauthenticated 401 proves the mobile route exists; it does not sign the app in.
    Account(MobilePaths.me),
    Websites(MobilePaths.websites),
    Email(MobilePaths.emails),
    Sms(MobilePaths.smsInvestigations),
    Timeline(MobilePaths.timeline),
}

/** What an unauthenticated probe of a feature's route says about it. */
sealed interface FeatureAvailability {
    /** 401: the route exists on this server and needs a signed-in session. */
    data object RequiresSignIn : FeatureAvailability

    /** 403: the route exists but only answers requests from the backend machine itself. */
    data object LocalOnly : FeatureAvailability

    /** 404: this server does not provide the route (e.g. the hosted ingest-only deployment). */
    data object NotDeployed : FeatureAvailability

    /** Any other status, or no answer. */
    data class Unknown(val httpCode: Int?) : FeatureAvailability
}

sealed interface ServerHealth {
    data class Online(val version: String) : ServerHealth

    /** The server answered, but not like a LeakTrace backend (wrong status code or body). */
    data class Unexpected(val httpCode: Int) : ServerHealth

    data object Unreachable : ServerHealth
}

data class ServerReport(
    val baseUrl: String,
    val health: ServerHealth,
    val features: Map<BackendFeature, FeatureAvailability>,
    val checkedAtMillis: Long,
)
