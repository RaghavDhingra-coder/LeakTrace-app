package com.codecrafters.leaktrace.ui.mobile

import com.codecrafters.leaktrace.data.remote.ApiResult
import com.codecrafters.leaktrace.data.remote.EmailPageDto
import com.codecrafters.leaktrace.data.remote.CallAnalysisDto
import com.codecrafters.leaktrace.data.remote.EmailDto
import com.codecrafters.leaktrace.data.remote.ExposureDto
import com.codecrafters.leaktrace.data.remote.WebsiteDto
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

    @Test fun passwordLoginStoresOnlyTokenAndLoadsAccount() = runTest(dispatcher) {
        val store = MemoryCredentials()
        val repo = FakeRepository().apply { loginResult = ApiResult.Success(MobileTokenDto("login-token", "future", "a@example.test", listOf("account:read"))) }
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        model.login("a@example.test", "synthetic-password")
        advanceUntilIdle()
        assertEquals("login-token", store.savedToken?.token)
        assertTrue(model.session.value is MobileSessionState.Connected)
        assertEquals(MobileLoadState.Empty, model.emails.value)
        model.signOut()
        advanceUntilIdle()
        assertEquals(null, store.savedToken)
        assertEquals(MobileLoadState.NeedsAccount, model.emails.value)
    }

    @Test fun lateLoginCannotRestoreSignedOutSession() = runTest(dispatcher) {
        val gate = CompletableDeferred<ApiResult<MobileTokenDto>>()
        val repo = FakeRepository().apply { loginGate = gate }
        val store = MemoryCredentials()
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        model.login("a@example.test", "synthetic-password")
        runCurrent()
        model.signOut()
        runCurrent()
        gate.complete(ApiResult.Success(MobileTokenDto("late-token", "future", "a@example.test", emptyList())))
        advanceUntilIdle()
        assertEquals(null, store.savedToken)
        assertEquals(MobileSessionState.SignedOut, model.session.value)
    }

    @Test fun loginErrorsRemainDistinctAndStoreNothing() = runTest(dispatcher) {
        val store = MemoryCredentials()
        val repo = FakeRepository()
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        for ((error, reason) in listOf(ApiResult.Unauthorized to MobileFailure.InvalidCredentials,
            ApiResult.Unverified to MobileFailure.Unverified, ApiResult.Disabled to MobileFailure.Disabled,
            ApiResult.NetworkError to MobileFailure.Network)) {
            repo.loginResult = error
            model.login("a@example.test", "synthetic-password")
            advanceUntilIdle()
            assertEquals(MobileSessionState.Error(reason), model.session.value)
            assertEquals(null, store.savedToken)
        }
    }

    @Test fun refreshReplacesEmailAndWebsiteSnapshotsAndLogoutClearsThem() = runTest(dispatcher) {
        val exposure = ExposureDto(7, "Facebook", "recipient_alias", "a@leaktrap.xyz", "gmail.com", "facebook.com", "medium", "Possible source", "Not proof")
        fun email(id: Long) = EmailDto(exposure = if (id == 2L) listOf(exposure) else emptyList(),
            id = id, receivedAt = "2026-01-0${id}T00:00:00Z", createdAt = "2026-01-0${id}T00:00:00Z",
            matchStatus = null, matchConfidence = null, recipientKnown = true, signupId = 7,
            siteName = "Facebook", siteDomain = "facebook.com", senderDomain = "gmail.com")
        fun website(alias: String) = WebsiteDto(7, "Facebook", "facebook.com", alias, "Ember", false, 0, 0, "2026-01-01T00:00:00Z")
        val repo = FakeRepository().apply {
            emailResult = ApiResult.Success(EmailPageDto(listOf(email(1)), 1, 20, 0))
            websiteResult = ApiResult.Success(WebsitePageDto(listOf(website("old@leaktrap.xyz")), 1, 20, 0))
        }
        val store = MemoryCredentials().apply { savedToken = MobileTokenDto("alice-token", "future", "alice@example.test", listOf("email:read", "websites:read")) }
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        assertEquals(listOf(1L), model.emailSnapshot.value!!.items.map { it.id })
        repo.emailResult = ApiResult.Success(EmailPageDto(listOf(email(2), email(1)), 2, 20, 0))
        repo.websiteResult = ApiResult.Success(WebsitePageDto(listOf(website("new@leaktrap.xyz")), 1, 20, 0))
        model.refreshEmails(); model.refreshWebsites(); advanceUntilIdle()
        assertEquals(listOf(2L, 1L), model.emailSnapshot.value!!.items.map { it.id })
        assertEquals("Facebook", model.emailSnapshot.value!!.items.first().exposure.single().siteName)
        assertEquals("new@leaktrap.xyz", model.websiteSnapshot.value!!.items.single().alias)
        repo.websiteResult = ApiResult.Success(WebsitePageDto(emptyList(), 0, 20, 0))
        model.refreshWebsites(); advanceUntilIdle()
        assertEquals(MobileLoadState.Empty, model.websites.value)
        assertTrue(model.websiteSnapshot.value!!.items.isEmpty())
        repo.emailResult = ApiResult.NetworkError
        model.refreshEmails(); advanceUntilIdle()
        assertEquals(MobileLoadState.Error(MobileFailure.Network), model.emails.value)
        assertEquals(listOf(2L, 1L), model.emailSnapshot.value!!.items.map { it.id })
        model.signOut(); advanceUntilIdle()
        assertEquals(null, model.emailSnapshot.value)
        assertEquals(null, model.websiteSnapshot.value)
        repo.emailResult = ApiResult.Success(EmailPageDto(emptyList(), 0, 20, 0))
        repo.websiteResult = ApiResult.Success(WebsitePageDto(emptyList(), 0, 20, 0))
        repo.loginResult = ApiResult.Success(MobileTokenDto("bob-token", "future", "bob@example.test", listOf("email:read", "websites:read")))
        model.login("bob@example.test", "synthetic-password"); advanceUntilIdle()
        assertEquals(MobileLoadState.Empty, model.emails.value)
        assertTrue(model.emailSnapshot.value!!.items.isEmpty())
        assertTrue(model.websiteSnapshot.value!!.items.isEmpty())
    }

    @Test fun callResultCannotSurviveLogoutOrAnotherAccount() = runTest(dispatcher) {
        val call = CallAnalysisDto(status = "possible", redactedTranscript = "Hi Ember", entities = emptyList(),
            candidates = emptyList(), exposure = emptyList(), statements = emptyList(), missing = emptyList(), notice = "Unverified")
        val repo = FakeRepository().apply { callResult = ApiResult.Success(call) }
        val store = MemoryCredentials().apply { savedToken = MobileTokenDto("alice-token", "future", "alice@example.test", listOf("call:analyze")) }
        val model = MobileViewModel(repo, store, dispatcher)
        advanceUntilIdle()
        model.analyzeCall("Hi Ember", false, false)
        advanceUntilIdle()
        assertEquals("Hi Ember", (model.callResult.value as MobileLoadState.Ready).value.redactedTranscript)
        model.signOut(); advanceUntilIdle()
        assertEquals(MobileLoadState.NeedsAccount, model.callResult.value)
        repo.loginResult = ApiResult.Success(MobileTokenDto("bob-token", "future", "bob@example.test", listOf("call:analyze")))
        model.login("bob@example.test", "synthetic-password"); advanceUntilIdle()
        assertEquals(MobileLoadState.NeedsAccount, model.callResult.value)
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
        var loginGate: CompletableDeferred<ApiResult<MobileTokenDto>>? = null
        var loginResult: ApiResult<MobileTokenDto> = ApiResult.Unauthorized
        override suspend fun login(email: String, password: String) = loginGate?.await() ?: loginResult
        var startGate: CompletableDeferred<ApiResult<PairingStartedDto>>? = null
        var emailGate: CompletableDeferred<ApiResult<EmailPageDto>>? = null
        var exchangeResult: ApiResult<MobileTokenDto> = ApiResult.Pending
        var meCalls = 0
        var meResult: ApiResult<MobileMeDto> = ApiResult.Success(MobileMeDto("a@example.test", true, listOf("account:read"), MobileDeviceDto(1, "Android", "now", "now", "future")))
        var callResult: ApiResult<CallAnalysisDto> = ApiResult.NotDeployed
        var emailResult: ApiResult<EmailPageDto> = ApiResult.Success(EmailPageDto(emptyList(), 0, 50, 0))
        var websiteResult: ApiResult<WebsitePageDto> = ApiResult.Success(WebsitePageDto(emptyList(), 0, 50, 0))
        override suspend fun startPairing(secretHash: String, deviceLabel: String) = startGate?.await() ?: ApiResult.Success(PairingStartedDto(1, "ABCD-1234", "future"))
        override suspend fun exchange(pairingId: Long, secret: String): ApiResult<MobileTokenDto> = exchangeResult
        override suspend fun me(token: String): ApiResult<MobileMeDto> { meCalls++; return meResult }
        override suspend fun logout(token: String) = ApiResult.Success(Unit)
        override suspend fun websites(token: String): ApiResult<WebsitePageDto> = websiteResult
        override suspend fun emails(token: String) = emailGate?.await() ?: emailResult
        override suspend fun timeline(token: String): ApiResult<TimelineDto> = ApiResult.Success(TimelineDto(emptyList(), "notice"))
        override suspend fun analyzeSms(token: String, text: String, sender: String?): ApiResult<SmsAnalysisDto> = ApiResult.NotDeployed
        override suspend fun analyzeCall(token: String, transcript: String, save: Boolean, storeRedactedTranscript: Boolean) = callResult
    }
}
