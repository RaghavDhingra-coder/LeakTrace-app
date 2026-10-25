package com.codecrafters.leaktrace.ui.account

import com.codecrafters.leaktrace.data.server.BackendFeature
import com.codecrafters.leaktrace.data.server.FeatureAvailability
import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.ui.server.ServerUiState

/** A route probe describes access, not an authenticated Android session. */
internal enum class AccountAccessState {
    Checking,
    PairingAvailable,
    LocalOnly,
    NotDeployed,
    Unavailable,
}

internal fun ServerUiState.accountAccessState(): AccountAccessState {
    val currentReport = report ?: return AccountAccessState.Checking
    if (currentReport.health !is ServerHealth.Online) return AccountAccessState.Unavailable
    return when (currentReport.features[BackendFeature.Account]) {
        FeatureAvailability.RequiresSignIn -> AccountAccessState.PairingAvailable
        FeatureAvailability.LocalOnly -> AccountAccessState.LocalOnly
        FeatureAvailability.NotDeployed -> AccountAccessState.NotDeployed
        else -> AccountAccessState.Unavailable
    }
}
