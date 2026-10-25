package com.codecrafters.leaktrace.ui.mobile

import com.codecrafters.leaktrace.data.remote.ApiResult
import com.codecrafters.leaktrace.data.remote.EmailPageDto
import com.codecrafters.leaktrace.data.remote.MobileCredentialStore
import com.codecrafters.leaktrace.data.remote.MobileDeviceDto
import com.codecrafters.leaktrace.data.remote.MobileMeDto
import com.codecrafters.leaktrace.data.remote.MobileRepository
import com.codecrafters.leaktrace.data.remote.MobileTokenDto
import com.codecrafters.leaktrace.data.remote.PairingStartedDto
import com.codecrafters.leaktrace.data.remote.PendingPairing
import com.codecrafters.leaktrace.data.remote.SmsAnalysisDto
import com.codecrafters.leaktrace.data.remote.TimelineDto
import com.codecrafters.leaktrace.data.remote.WebsitePageDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun signedOutDoesNotFetchPrivateData() = runTest(dispatcher) {
        val repo = FakeRepository()
        val model = MobileViewModel(repo, MemoryCredentials(), dispatcher)
        advanceUntilIdle()
        assertEquals(MobileSessionState.SignedOut, model.session.value)
        assertEquals(MobileLoadState.NeedsAccount, model.websites.value)
        assertEquals(0, repo.meCalls)
    }

    @Test fun authenticatedEmptyAndNetworkErrorStayDistinct() = runTest(dispatcher) {
        val repo = FakeRepository()
        repo.emailResult = ApiResult.NetworkError
        val store = MemoryCredentials().apply { savedToken = MobileTokenDto("secret", "future", "a@example.test", listOf("account:read")) }
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        assertTrue(model.session.value is MobileSessionState.Connected)
        assertEquals(MobileLoadState.Empty, model.websites.value)
        assertEquals(MobileLoadState.Error(MobileFailure.Network), model.emails.value)
    }

    @Test fun unauthorizedClearsStoredTokenAndAccountData() = runTest(dispatcher) {
        val repo = FakeRepository().apply { meResult = ApiResult.Unauthorized }
        val store = MemoryCredentials().apply { savedToken = MobileTokenDto("secret", "future", "a@example.test", listOf("account:read")) }
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        assertEquals(MobileSessionState.Expired, model.session.value)
        assertEquals(null, store.savedToken)
        assertEquals(MobileLoadState.SessionExpired, model.timeline.value)
    }

    @Test fun latePrivateResponseCannotRepopulateDataAfterSignOut() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<EmailPageDto>>()
        val repo = FakeRepository().apply { emailGate = response }
        val store = MemoryCredentials().apply { savedToken = MobileTokenDto("secret", "future", "a@example.test", listOf("account:read")) }
        val model = MobileViewModel(repo, store, dispatcher)
        runCurrent()
        assertEquals(MobileLoadState.Loading, model.emails.value)
        model.signOut()
        runCurrent()
        response.complete(ApiResult.Success(EmailPageDto(emptyList(), 0, 20, 0)))
        advanceUntilIdle()
        assertEquals(MobileSessionState.SignedOut, model.session.value)
        assertEquals(MobileLoadState.NeedsAccount, model.emails.value)
        assertEquals(null, store.savedToken)
    }

    @Test fun latePairingStartCannotRestorePendingCredentialsAfterSignOut() = runTest(dispatcher) {
        val response = CompletableDeferred<ApiResult<PairingStartedDto>>()
        val repo = FakeRepository().apply { startGate = response }
        val store = MemoryCredentials()
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        model.startPairing()
        runCurrent()
        model.signOut()
        runCurrent()
        response.complete(ApiResult.Success(PairingStartedDto(9, "ABCD-1234", "future")))
        advanceUntilIdle()
        assertEquals(MobileSessionState.SignedOut, model.session.value)
        assertEquals(null, store.savedPending)
        assertEquals(null, store.savedToken)
    }

    @Test fun unknownPairingCanRestartWithoutDeclaringApiUnavailable() = runTest(dispatcher) {
        val repo = FakeRepository().apply { exchangeResult = ApiResult.PairingNotFound }
        val store = MemoryCredentials().apply { savedPending = PendingPairing(1, "ABCD-1234", "future", "secret") }
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        model.checkPairing()
        advanceUntilIdle()
        assertEquals(MobileSessionState.Expired, model.session.value)
        assertEquals(null, store.savedPending)
    }

    @Test fun approvedPairingStoresTokenAndLoadsAccountSections() = runTest(dispatcher) {
        val repo = FakeRepository().apply { exchangeResult = ApiResult.Success(MobileTokenDto("test-token", "future", "a@example.test", listOf("account:read"))) }
        val store = MemoryCredentials()
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        model.startPairing()
        advanceUntilIdle()
        assertTrue(model.session.value is MobileSessionState.Pairing)
        assertEquals(null, store.savedToken)
        model.checkPairing()
        advanceUntilIdle()
        assertTrue(model.session.value is MobileSessionState.Connected)
        assertEquals("test-token", store.savedToken?.token)
        assertEquals(null, store.savedPending)
        assertEquals(MobileLoadState.Empty, model.websites.value)
        assertEquals(MobileLoadState.Empty, model.emails.value)
        assertEquals(MobileLoadState.Empty, model.timeline.value)
    }

    private class MemoryCredentials : MobileCredentialStore {
        var savedToken: MobileTokenDto? = null
        var savedPending: PendingPairing? = null
        override fun token() = savedToken
        override fun saveToken(value: MobileTokenDto) { savedToken = value }
        override fun clearToken() { savedToken = null }
        override fun pending() = savedPending
        override fun savePending(value: PendingPairing) { savedPending = value }
        override fun clearPending() { savedPending = null }
    }

    private class FakeRepository : MobileRepository {
        var startGate: CompletableDeferred<ApiResult<PairingStartedDto>>? = null
        var emailGate: CompletableDeferred<ApiResult<EmailPageDto>>? = null
        var exchangeResult: ApiResult<MobileTokenDto> = ApiResult.Pending
        var meCalls = 0
        var meResult: ApiResult<MobileMeDto> = ApiResult.Success(MobileMeDto("a@example.test", true, listOf("account:read"), MobileDeviceDto(1, "Android", "now", "now", "future")))
        var emailResult: ApiResult<EmailPageDto> = ApiResult.Success(EmailPageDto(emptyList(), 0, 50, 0))
        override suspend fun startPairing(secretHash: String, deviceLabel: String) = startGate?.await() ?: ApiResult.Success(PairingStartedDto(1, "ABCD-1234", "future"))
        override suspend fun exchange(pairingId: Long, secret: String): ApiResult<MobileTokenDto> = exchangeResult
        override suspend fun me(token: String): ApiResult<MobileMeDto> { meCalls++; return meResult }
        override suspend fun logout(token: String) = ApiResult.Success(Unit)
        override suspend fun websites(token: String): ApiResult<WebsitePageDto> = ApiResult.Success(WebsitePageDto(emptyList(), 0, 50, 0))
        override suspend fun emails(token: String) = emailGate?.await() ?: emailResult
        override suspend fun timeline(token: String): ApiResult<TimelineDto> = ApiResult.Success(TimelineDto(emptyList(), "notice"))
        override suspend fun analyzeSms(token: String, text: String, sender: String?): ApiResult<SmsAnalysisDto> = ApiResult.NotDeployed
    }
}
