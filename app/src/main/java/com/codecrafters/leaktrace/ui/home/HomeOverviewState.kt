package com.codecrafters.leaktrace.ui.home

import com.codecrafters.leaktrace.data.server.ServerHealth
import com.codecrafters.leaktrace.ui.server.ServerUiState

/** Home activity is empty until an authenticated account data source exists. */
internal enum class HomeOverviewState { Loading, Empty, Error }

internal fun ServerUiState.homeOverviewState(): HomeOverviewState = when (report?.health) {
    null -> HomeOverviewState.Loading
    is ServerHealth.Online -> HomeOverviewState.Empty
    else -> HomeOverviewState.Error
}
