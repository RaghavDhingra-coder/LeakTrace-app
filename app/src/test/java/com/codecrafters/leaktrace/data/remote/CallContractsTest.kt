package com.codecrafters.leaktrace.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CallContractsTest {
    private val base = "https://mobile.example"
    private val result = """{"id":42,"created_at":"2026-10-26T00:00:00Z","status":"possible","redacted_transcript":"Hi Ember","entities":[{"key":"person:0","type":"person","display":"Ember","value":"Ember","certainty":"pattern","sensitive":false,"note":null,"excluded":false,"relation":"Nickname for Facebook","signup_id":7}],"candidates":[{"key":"signup:7","name":"Facebook","domain":"facebook.com","signup_id":7,"status":"possible","signals":[{"type":"nickname_in_message","explanation":"Recorded nickname appears","excerpt":null,"weak":false}],"unknowns":[],"email_count":2,"last_email_at":null,"sms_count":1}],"exposure":[{"signup_id":7,"site_name":"Facebook","marker_type":"nickname_mention","marker":"Ember","observed_source":null,"expected_domain":"facebook.com","confidence":"low","explanation":"Possible source","caveat":"Not proof of a leak"}],"statements":[],"missing":[],"notice":"Caller unverified"}"""
    @Test fun transcriptRequestUsesOnlyBearerAndTextAndReadsEvidence() = runTest {
        var path = ""; var payload = ""; var bearer: String? = null
        val repo = HttpMobileRepository(base, object : HttpClient {
            override suspend fun get(url: String) = error("Unexpected GET")
            override suspend fun request(method: String, url: String, body: String?, token: String?): HttpResponse {
                assertEquals("POST", method); path = url; payload = body.orEmpty(); bearer = token
                return HttpResponse(200, result)
            }
        })
        val parsed = repo.analyzeCall("token", "Hi Ember", false, false) as ApiResult.Success
        assertEquals("$base/mobile/v1/calls/analyze", path)
        assertEquals("token", bearer)
        assertEquals("Hi Ember", LeakTraceJson.decodeFromString<CallAnalyzeRequestDto>(payload).transcript)
        assertFalse(payload.contains("audio"))
        assertFalse(LeakTraceJson.decodeFromString<CallAnalyzeRequestDto>(payload).save)
        assertEquals("Facebook", parsed.value.exposure.single().siteName)
        assertNull(parsed.value.exposure.single().observedSource)
        assertEquals(2, parsed.value.candidates.single().emailCount)
        assertTrue(parsed.value.notice.contains("unverified"))
    }
    @Test fun unauthorizedAndOfflineRemainDistinct() = runTest {
        fun repo(code: Int) = HttpMobileRepository(base, object : HttpClient {
            override suspend fun get(url: String) = HttpResponse(code, "{}")
            override suspend fun request(method: String, url: String, body: String?, bearer: String?) = HttpResponse(code, "{}")
        })
        assertEquals(ApiResult.Unauthorized, repo(401).analyzeCall("t", "Hi", false, false))
        assertEquals(ApiResult.Forbidden, repo(403).analyzeCall("t", "Hi", true, true))
        assertEquals(ApiResult.NotDeployed, repo(404).calls("t"))
        val offline = HttpMobileRepository(base, object : HttpClient {
            override suspend fun get(url: String): HttpResponse = throw IOException()
            override suspend fun request(method: String, url: String, body: String?, bearer: String?): HttpResponse = throw IOException()
        })
        assertEquals(ApiResult.NetworkError, offline.analyzeCall("t", "Hi", false, false))
    }
}
