package com.codecrafters.leaktrace.ui.components

import com.codecrafters.leaktrace.data.account.AccountDataAccess
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDataUiStateTest {
    @Test fun noDataNoticeOnlyAppearsWhenAccessIsSettledButRecordsCannotLoad() {
        assertTrue(AccountDataUiState(AccountDataAccess.AuthenticationRequired, false).showNoDataNotice)
        assertTrue(AccountDataUiState(AccountDataAccess.NotDeployed, false).showNoDataNotice)
        assertTrue(AccountDataUiState(AccountDataAccess.DataContractMissing, false).showNoDataNotice)
        assertFalse(AccountDataUiState(AccountDataAccess.Loading, true).showNoDataNotice)
        assertFalse(AccountDataUiState(AccountDataAccess.NetworkError, false).showNoDataNotice)
        assertFalse(AccountDataUiState(AccountDataAccess.UnexpectedResponse(500), false).showNoDataNotice)
    }
}
