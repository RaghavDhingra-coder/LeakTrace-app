package com.codecrafters.leaktrace.ui.components

import com.codecrafters.leaktrace.data.account.AccountDataAccess

/** Screen state based on access probes. A true empty collection cannot be known until records load. */
internal data class AccountDataUiState(
    val access: AccountDataAccess,
    val isRefreshing: Boolean,
) {
    val showNoDataNotice: Boolean get() = when (access) {
        AccountDataAccess.AuthenticationRequired,
        AccountDataAccess.LocalOnly,
        AccountDataAccess.NotDeployed,
        AccountDataAccess.DataContractMissing -> true
        AccountDataAccess.Loading,
        AccountDataAccess.NetworkError,
        is AccountDataAccess.UnexpectedResponse -> false
    }
}
