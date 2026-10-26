package com.codecrafters.leaktrace.ui.mobile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.codecrafters.leaktrace.LeakTraceApplication
import com.codecrafters.leaktrace.data.remote.ApiResult
import com.codecrafters.leaktrace.data.remote.EmailPageDto
import com.codecrafters.leaktrace.data.remote.MobileCredentialStore
import com.codecrafters.leaktrace.data.remote.MobileMeDto
import com.codecrafters.leaktrace.data.remote.MobileRepository
import com.codecrafters.leaktrace.data.remote.PendingPairing
import com.codecrafters.leaktrace.data.remote.SmsAnalysisDto
import com.codecrafters.leaktrace.data.remote.TimelineDto
import com.codecrafters.leaktrace.data.remote.WebsitePageDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

sealed interface MobileSessionState {
    data object Checking : MobileSessionState
    data object SignedOut : MobileSessionState
    data class Pairing(val code: String, val expiresAt: String, val checking: Boolean = false) : MobileSessionState
    data class Connected(val account: MobileMeDto) : MobileSessionState
    data object Expired : MobileSessionState
    data object Unavailable : MobileSessionState
    data class Error(val reason: MobileFailure) : MobileSessionState
}

enum class MobileFailure { Network, RateLimited, InvalidResponse, Storage, Server, Forbidden, InvalidRequest, InvalidCredentials, Unverified, Disabled, LoginUnavailable }

sealed interface MobileLoadState<out T> {
    data object NeedsAccount : MobileLoadState<Nothing>
    data object Loading : MobileLoadState<Nothing>
    data object Empty : MobileLoadState<Nothing>
    data class Ready<T>(val value: T) : MobileLoadState<T>
    data object SessionExpired : MobileLoadState<Nothing>
    data object Unavailable : MobileLoadState<Nothing>
    data class Error(val reason: MobileFailure) : MobileLoadState<Nothing>
}

/** One activity-scoped owner for the mobile session and its authenticated read results. */
class MobileViewModel(
    private val repository: MobileRepository,
    private val credentials: MobileCredentialStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _session = MutableStateFlow<MobileSessionState>(MobileSessionState.Checking)
    val session: StateFlow<MobileSessionState> = _session.asStateFlow()
    private val _websites = MutableStateFlow<MobileLoadState<WebsitePageDto>>(MobileLoadState.NeedsAccount)
    val websites = _websites.asStateFlow()
    private val _emails = MutableStateFlow<MobileLoadState<EmailPageDto>>(MobileLoadState.NeedsAccount)
    val emails = _emails.asStateFlow()
    private val _timeline = MutableStateFlow<MobileLoadState<TimelineDto>>(MobileLoadState.NeedsAccount)
    val timeline = _timeline.asStateFlow()
    private val _sms = MutableStateFlow<MobileLoadState<SmsAnalysisDto>>(MobileLoadState.NeedsAccount)
    val sms = _sms.asStateFlow()
    private var smsJob: Job? = null
    private var sessionJob: Job? = null
    private var sessionGeneration = 0

    init { refreshSession() }

    fun refreshSession() {
        val generation = ++sessionGeneration
        sessionJob?.cancel()
        clearData()
        sessionJob = viewModelScope.launch {
            _session.value = MobileSessionState.Checking
            val saved = try { withContext(ioDispatcher) { credentials.token() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { _session.value = MobileSessionState.Error(MobileFailure.Storage); return@launch }
            if (generation != sessionGeneration) return@launch
            if (saved == null) {
                val pending = try { withContext(ioDispatcher) { credentials.pending() } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { _session.value = MobileSessionState.Error(MobileFailure.Storage); return@launch }
                _session.value = pending?.let { MobileSessionState.Pairing(it.code, it.expiresAt) } ?: MobileSessionState.SignedOut
                return@launch
            }
            val result = repository.me(saved.token)
            if (generation != sessionGeneration) return@launch
            when (result) {
                is ApiResult.Success -> {
                    _session.value = MobileSessionState.Connected(result.value)
                    refreshAll()
                }
                ApiResult.Unauthorized -> expireSession()
                ApiResult.NotDeployed -> _session.value = MobileSessionState.Unavailable
                else -> _session.value = MobileSessionState.Error(result.failure())
            }
        }
    }

    fun login(email: String, password: String) {
        val generation = ++sessionGeneration
        sessionJob?.cancel()
        clearData()
        sessionJob = viewModelScope.launch {
            _session.value = MobileSessionState.Checking
            val result = repository.login(email.trim(), password)
            if (generation != sessionGeneration) return@launch
            when (result) {
                is ApiResult.Success -> {
                    try {
                        // Synchronous secure preference update cannot race sign-out on the main thread.
                        credentials.saveToken(result.value)
                        credentials.clearPending()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        try { credentials.clearToken() } catch (_: Exception) { /* Report storage failure below. */ }
                        _session.value = MobileSessionState.Error(MobileFailure.Storage)
                        repository.logout(result.value.token)
                        return@launch
                    }
                    refreshSession()
                }
                ApiResult.Unauthorized -> _session.value = MobileSessionState.Error(MobileFailure.InvalidCredentials)
                ApiResult.NotDeployed -> _session.value = MobileSessionState.Error(MobileFailure.LoginUnavailable)
                else -> _session.value = MobileSessionState.Error(result.failure())
            }
        }
    }

    fun startPairing() {
        val generation = ++sessionGeneration
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            _pairingError.value = null
            _session.value = MobileSessionState.Checking
            val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
            val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            val result = repository.startPairing(hash, "Android device")
            if (generation != sessionGeneration) return@launch
            when (result) {
                is ApiResult.Success -> {
                    val pending = PendingPairing(result.value.pairingId, result.value.code, result.value.expiresAt, secret)
                    try { credentials.savePending(pending) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { _session.value = MobileSessionState.Error(MobileFailure.Storage); return@launch }
                    _session.value = MobileSessionState.Pairing(pending.code, pending.expiresAt)
                }
                ApiResult.NotDeployed -> _session.value = MobileSessionState.Unavailable
                else -> _session.value = MobileSessionState.Error(result.failure())
            }
        }
    }

    fun checkPairing() {
        val generation = ++sessionGeneration
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            _pairingError.value = null
            val pending = try { withContext(ioDispatcher) { credentials.pending() } }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { _session.value = MobileSessionState.Error(MobileFailure.Storage); return@launch }
            if (generation != sessionGeneration) return@launch
            if (pending == null) { _session.value = MobileSessionState.SignedOut; return@launch }
            _session.value = MobileSessionState.Pairing(pending.code, pending.expiresAt, checking = true)
            val result = repository.exchange(pending.pairingId, pending.secret)
            if (generation != sessionGeneration) return@launch
            when (result) {
                ApiResult.Pending -> _session.value = MobileSessionState.Pairing(pending.code, pending.expiresAt)
                is ApiResult.Success -> {
                    try {
                        credentials.saveToken(result.value)
                        credentials.clearPending()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { _session.value = MobileSessionState.Error(MobileFailure.Storage); return@launch }
                    refreshSession()
                }
                ApiResult.Expired, ApiResult.PairingNotFound -> {
                    credentials.clearPending()
                    _session.value = MobileSessionState.Expired
                }
                ApiResult.NotDeployed -> _session.value = MobileSessionState.Unavailable
                else -> {
                    _session.value = MobileSessionState.Pairing(pending.code, pending.expiresAt)
                    _pairingError.value = result.failure()
                }
            }
        }
    }

    private val _pairingError = MutableStateFlow<MobileFailure?>(null)
    val pairingError = _pairingError.asStateFlow()

    fun signOut() {
        ++sessionGeneration
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            val token = try { withContext(ioDispatcher) { credentials.token()?.token } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            try { credentials.clearToken(); credentials.clearPending() }
            catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { _session.value = MobileSessionState.Error(MobileFailure.Storage); return@launch }
            _session.value = MobileSessionState.SignedOut
            clearData()
            if (token != null) repository.logout(token)
        }
    }

    fun refreshAll() { refreshWebsites(); refreshEmails(); refreshTimeline() }
    fun refreshWebsites() = load(_websites, { repository.websites(it) }, { it.items.isEmpty() })
    fun refreshEmails() = load(_emails, { repository.emails(it) }, { it.items.isEmpty() })
    fun refreshTimeline() = load(_timeline, { repository.timeline(it) }, { it.items.isEmpty() })

    /** Call only after an explicit user action; this uploads the pasted text to the paired server. */
    fun analyzeSms(text: String, sender: String?) {
        smsJob?.cancel()
        if (text.isBlank() || text.length > 2000 || (sender?.length ?: 0) > 64) {
            _sms.value = MobileLoadState.Error(MobileFailure.InvalidRequest)
            return
        }
        smsJob = load(_sms, { repository.analyzeSms(it, text, sender) }, { false })
    }
    fun clearSms() { smsJob?.cancel(); _sms.value = MobileLoadState.NeedsAccount }

    private fun <T> load(
        target: MutableStateFlow<MobileLoadState<T>>,
        fetch: suspend (String) -> ApiResult<T>,
        isEmpty: (T) -> Boolean,
    ): Job {
        val generation = sessionGeneration
        return viewModelScope.launch {
            val token = try { withContext(ioDispatcher) { credentials.token()?.token } } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            if (generation != sessionGeneration) return@launch
            if (token == null) { target.value = MobileLoadState.NeedsAccount; return@launch }
            target.value = MobileLoadState.Loading
            val result = fetch(token)
            if (generation != sessionGeneration) return@launch
            if (_session.value is MobileSessionState.Expired) {
                target.value = MobileLoadState.SessionExpired
                return@launch
            }
            when (result) {
                is ApiResult.Success -> target.value = if (isEmpty(result.value)) MobileLoadState.Empty else MobileLoadState.Ready(result.value)
                ApiResult.Unauthorized -> { target.value = MobileLoadState.SessionExpired; expireSession() }
                ApiResult.NotDeployed -> target.value = MobileLoadState.Unavailable
                else -> target.value = MobileLoadState.Error(result.failure())
            }
        }
    }

    private suspend fun expireSession() {
        credentials.clearToken()
        smsJob?.cancel()
        _session.value = MobileSessionState.Expired
        _websites.value = MobileLoadState.SessionExpired
        _emails.value = MobileLoadState.SessionExpired
        _timeline.value = MobileLoadState.SessionExpired
        _sms.value = MobileLoadState.SessionExpired
    }

    private fun clearData() {
        smsJob?.cancel()
        _websites.value = MobileLoadState.NeedsAccount
        _emails.value = MobileLoadState.NeedsAccount
        _timeline.value = MobileLoadState.NeedsAccount
        _sms.value = MobileLoadState.NeedsAccount
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LeakTraceApplication
                MobileViewModel(app.container.mobileRepository, app.container.credentialStore)
            }
        }
    }
}

private fun ApiResult<*>.failure(): MobileFailure = when (this) {
    ApiResult.NetworkError -> MobileFailure.Network
    ApiResult.RateLimited -> MobileFailure.RateLimited
    ApiResult.InvalidResponse -> MobileFailure.InvalidResponse
    ApiResult.Unverified -> MobileFailure.Unverified
    ApiResult.Disabled -> MobileFailure.Disabled
    ApiResult.Forbidden -> MobileFailure.Forbidden
    ApiResult.InvalidRequest -> MobileFailure.InvalidRequest
    else -> MobileFailure.Server
}
