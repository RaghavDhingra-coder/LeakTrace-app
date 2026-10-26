package com.codecrafters.leaktrace.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data object Pending : ApiResult<Nothing>
    data object Unauthorized : ApiResult<Nothing>
    data object Unverified : ApiResult<Nothing>
    data object Disabled : ApiResult<Nothing>
    data object Forbidden : ApiResult<Nothing>
    data object NotDeployed : ApiResult<Nothing>
    data object Expired : ApiResult<Nothing>
    data object PairingNotFound : ApiResult<Nothing>
    data object RateLimited : ApiResult<Nothing>
    data object InvalidRequest : ApiResult<Nothing>
    data object NetworkError : ApiResult<Nothing>
    data object InvalidResponse : ApiResult<Nothing>
    data class ServerError(val code: Int) : ApiResult<Nothing>
}

interface MobileRepository {
    suspend fun login(email: String, password: String): ApiResult<MobileTokenDto> = ApiResult.NotDeployed
    suspend fun startPairing(secretHash: String, deviceLabel: String): ApiResult<PairingStartedDto>
    suspend fun exchange(pairingId: Long, secret: String): ApiResult<MobileTokenDto>
    suspend fun me(token: String): ApiResult<MobileMeDto>
    suspend fun logout(token: String): ApiResult<Unit>
    suspend fun websites(token: String): ApiResult<WebsitePageDto>
    suspend fun emails(token: String): ApiResult<EmailPageDto>
    suspend fun timeline(token: String): ApiResult<TimelineDto>
    suspend fun analyzeSms(token: String, text: String, sender: String?): ApiResult<SmsAnalysisDto>
}

/** Uses only the mobile Bearer API. It never sends a web cookie or follows redirects. */
class HttpMobileRepository(private val baseUrl: String, private val http: HttpClient) : MobileRepository {
    init {
        val origin = java.net.URI(baseUrl)
        require(origin.scheme == "https" && !origin.host.isNullOrBlank() && origin.rawUserInfo == null &&
            origin.rawPath.isNullOrEmpty() && origin.rawQuery == null && origin.rawFragment == null) {
            "Mobile API requires an HTTPS origin"
        }
    }

    override suspend fun login(email: String, password: String): ApiResult<MobileTokenDto> = try {
        val response = http.request("POST", baseUrl + MobilePaths.login, LeakTraceJson.encodeToString(MobileLoginDto(email, password)))
        if (response.code == 200) ApiResult.Success(LeakTraceJson.decodeFromString<MobileTokenDto>(response.body))
        else if (response.code == 403) {
            when (LeakTraceJson.parseToJsonElement(response.body).jsonObject["detail"]?.jsonObject?.get("code")?.jsonPrimitive?.content) {
                "email_unverified" -> ApiResult.Unverified
                "account_disabled" -> ApiResult.Disabled
                else -> ApiResult.Forbidden
            }
        } else failure(response.code)
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: IOException) { ApiResult.NetworkError }
      catch (_: IllegalArgumentException) { ApiResult.InvalidResponse }

    override suspend fun startPairing(secretHash: String, deviceLabel: String) =
        call<PairingStartedDto>("POST", MobilePaths.pairings, LeakTraceJson.encodeToString(PairingStartDto(secretHash, deviceLabel)), null, 201)

    override suspend fun exchange(pairingId: Long, secret: String): ApiResult<MobileTokenDto> {
        val result = call<MobileTokenDto>("POST", "${MobilePaths.pairings}/$pairingId/token", LeakTraceJson.encodeToString(PairingExchangeDto(secret)), null, 200)
        // A resource 404 is not evidence that the mobile API is absent.
        return if (result == ApiResult.NotDeployed) ApiResult.PairingNotFound else result
    }

    override suspend fun me(token: String) = call<MobileMeDto>("GET", MobilePaths.me, null, token, 200)
    override suspend fun websites(token: String) = call<WebsitePageDto>("GET", MobilePaths.websites, null, token, 200)
    override suspend fun emails(token: String) = call<EmailPageDto>("GET", MobilePaths.emails, null, token, 200)
    override suspend fun timeline(token: String) = call<TimelineDto>("GET", MobilePaths.timeline, null, token, 200)
    override suspend fun analyzeSms(token: String, text: String, sender: String?) =
        call<SmsAnalysisDto>("POST", MobilePaths.smsAnalyze, LeakTraceJson.encodeToString(SmsAnalyzeRequestDto(text, sender)), token, 200)

    override suspend fun logout(token: String): ApiResult<Unit> = try {
        when (val code = http.request("POST", baseUrl + MobilePaths.logout, bearer = token).code) {
            204 -> ApiResult.Success(Unit)
            else -> failure(code)
        }
    } catch (_: IOException) {
        ApiResult.NetworkError
    }

    private suspend inline fun <reified T> call(
        method: String, path: String, body: String?, bearer: String?, successCode: Int,
    ): ApiResult<T> = try {
        val response = http.request(method, baseUrl + path, body, bearer)
        if (response.code == successCode) ApiResult.Success(LeakTraceJson.decodeFromString<T>(response.body))
        else failure(response.code)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        ApiResult.NetworkError
    } catch (_: SerializationException) {
        ApiResult.InvalidResponse
    } catch (_: IllegalArgumentException) {
        ApiResult.InvalidResponse
    }

    private fun failure(code: Int): ApiResult<Nothing> = when (code) {
        202 -> ApiResult.Pending
        401 -> ApiResult.Unauthorized
        403 -> ApiResult.Forbidden
        404 -> ApiResult.NotDeployed
        410 -> ApiResult.Expired
        429 -> ApiResult.RateLimited
        413, 415, 422 -> ApiResult.InvalidRequest
        else -> ApiResult.ServerError(code)
    }
}
