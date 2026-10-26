package com.codecrafters.leaktrace.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FeatureContractsTest {
    private fun repo(code: Int, json: String, capture: (String,String,String?,String?) -> Unit = {_,_,_,_ ->}) =
        HttpMobileRepository("https://mobile.example", object: HttpClient {
            override suspend fun get(url: String) = HttpResponse(code,json)
            override suspend fun request(method: String,url: String,body: String?,bearer: String?): HttpResponse {
                capture(method,url,body,bearer); return HttpResponse(code,json)
            }
        })
    @Test fun loginContractUsesHttpsWithoutBearerOrCookie() = runTest {
        val result = repo(200, """{"token":"synthetic-token","expires_at":"future","email":"alice@example.test","scopes":["account:read"]}""") { method,url,body,bearer ->
            assertEquals("POST",method)
            assertEquals("https://mobile.example/mobile/v1/login",url)
            assertNull(bearer)
            val request = LeakTraceJson.decodeFromString<MobileLoginDto>(body!!)
            assertEquals("alice@example.test",request.email)
            assertEquals("synthetic-password",request.password)
            assertFalse(request.toString().contains("synthetic-password"))
        }.login("alice@example.test","synthetic-password") as ApiResult.Success
        assertEquals("synthetic-token",result.value.token)
    }
    @Test fun loginErrorsAreNotAvailabilityOrSuccess() = runTest {
        assertEquals(ApiResult.Unverified, repo(403,"""{"detail":{"code":"email_unverified"}}""").login("e","p"))
        assertEquals(ApiResult.Disabled, repo(403,"""{"detail":{"code":"account_disabled"}}""").login("e","p"))
        assertEquals(ApiResult.Unauthorized, repo(401,"{}").login("e","p"))
        assertEquals(ApiResult.RateLimited, repo(429,"{}").login("e","p"))
        assertEquals(ApiResult.ServerError(503), repo(503,"{}").login("e","p"))
        assertEquals(ApiResult.InvalidResponse, repo(200,"{}").login("e","p"))
        val offline = HttpMobileRepository("https://mobile.example", object: HttpClient {
            override suspend fun get(url: String): HttpResponse = throw IOException()
            override suspend fun request(method: String,url: String,body: String?,bearer: String?): HttpResponse = throw IOException()
        })
        assertEquals(ApiResult.NetworkError, offline.login("e","p"))
    }
    @Test fun insecureOrNonOriginBaseIsRejectedBeforeCredentialsCanBeSent() {
        for (url in listOf("http://example.com","https://user@example.com","https://example.com/path","https://example.com?secret=x")) {
            assertThrows(IllegalArgumentException::class.java) { HttpMobileRepository(url, HttpClient { HttpResponse(200, "") }) }
        }
    }
    @Test fun canonicalEmailStatusOverridesLegacyMatcher() {
        val json = """{"id":1,"received_at":"now","created_at":"now","match_status":"matched","match_confidence":"high","recipient_known":false,"signup_id":null,"site_name":null,"site_domain":null,"sender_domain":"facebook.com","status":"unmatched","alias":"unknown@example.test","unread":true}"""
        val email = LeakTraceJson.decodeFromString<EmailDto>(json)
        assertEquals("Unmatched", email.attributionLabel())
        assertEquals("Failed", email.copy(status="failed").attributionLabel())
        assertEquals("Unprocessed", email.copy(status="unprocessed").attributionLabel())
        assertEquals("Attribution unavailable on this server", email.copy(status=null).attributionLabel())
    }
    @Test fun exposureDataStaysSeparateFromRecipientStatusAndScamRisk() {
        val evidence = """{"signup_id":7,"site_name":"Amazon","marker_type":"nickname_mention","marker":"Alice","observed_source":"gmail.com","expected_domain":"amazon.com","confidence":"low","explanation":"Possible source","caveat":"Not proof of a leak"}"""
        val exposure = LeakTraceJson.decodeFromString<ExposureDto>(evidence)
        assertEquals("Amazon", exposure.siteName)
        assertEquals("nickname_mention", exposure.markerType)
        assertEquals("gmail.com", exposure.observedSource)
        assertEquals("Not proof of a leak", exposure.caveat)
    }
    @Test fun structuredRiskPreservesBackendAssessmentReasonsAndUncertainty() {
        val risk = LeakTraceJson.decodeFromString<SmsRiskDto>("""{"assessment":"Suspicious","risk_level":"moderate","reasons":[{"code":"source_conflict","message":"Source could not be verified","evidence":"evil.example"}],"urls":["https://evil.example"],"domains":["evil.example"],"next_steps":["Verify independently"],"notice":"Not proof of a leak"}""")
        assertEquals("Suspicious",risk.assessment)
        assertEquals("moderate",risk.riskLevel)
        assertEquals("Source could not be verified",risk.reasons.single().message)
        assertEquals("Not proof of a leak",risk.notice)
    }
}
