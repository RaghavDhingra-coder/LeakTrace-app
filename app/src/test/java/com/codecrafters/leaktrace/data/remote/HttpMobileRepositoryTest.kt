package com.codecrafters.leaktrace.data.remote

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpMobileRepositoryTest {
    private val base = "https://mobile.example"

    private fun repository(response: HttpResponse, capture: (String, String, String?, String?) -> Unit = { _, _, _, _ -> }): MobileRepository =
        HttpMobileRepository(base, object : HttpClient {
            override suspend fun get(url: String) = response
            override suspend fun request(method: String, url: String, body: String?, bearer: String?): HttpResponse {
                capture(method, url, body, bearer)
                return response
            }
        })

    @Test fun pairingUsesConfirmedRouteAndJsonFields() = runTest {
        val sent = mutableListOf<String>()
        val repo = repository(HttpResponse(201, """{"pairing_id":9,"code":"ABCD-1234","expires_at":"2026-10-25T12:00:00Z"}""")) { method, url, body, bearer ->
            sent += listOf(method, url, body.orEmpty(), bearer.orEmpty())
        }
        assertTrue(repo.startPairing("hash", "Android") is ApiResult.Success)
        assertEquals("POST", sent[0])
        assertEquals("$base/mobile/v1/pairings", sent[1])
        assertTrue(sent[2].contains("\"secret_hash\":\"hash\""))
        assertEquals("", sent[3])
    }

    @Test fun pendingUnauthorizedAndNotDeployedRemainDistinct() = runTest {
        assertEquals(ApiResult.Pending, repository(HttpResponse(202, "")).exchange(9, "secret"))
        assertEquals(ApiResult.Unauthorized, repository(HttpResponse(401, "")).me("token"))
        assertEquals(ApiResult.NotDeployed, repository(HttpResponse(404, "")).websites("token"))
    }

    @Test fun websitesDecodeTypedPageAndBearer() = runTest {
        var auth: String? = null
        val json = """{"items":[{"id":1,"site_name":"Shop","site_domain":"shop.example","alias":"a@example.test","nickname":null,"real_name_required":false,"receipt_count":1,"email_count":2,"created_at":"2026-10-25T00:00:00Z"}],"total":1,"limit":50,"offset":0}"""
        val result = repository(HttpResponse(200, json)) { _, _, _, bearer -> auth = bearer }.websites("test-token")
        assertEquals("test-token", auth)
        assertEquals("Shop", (result as ApiResult.Success<WebsitePageDto>).value.items.single().siteName)
    }

    @Test fun malformedResponseIsNotShownAsEmpty() = runTest {
        assertEquals(ApiResult.InvalidResponse, repository(HttpResponse(200, "{} ")).emails("token"))
    }

    @Test fun smsComparisonUsesConfirmedRouteAndDoesNotClaimVerification() = runTest {
        var path = ""
        var payload = ""
        val json = """{"status":"unresolved","sender":null,"sender_notes":[],"received_at":null,"label":null,"redacted_text":"redacted","entities":[],"candidates":[],"missing":["No matching account evidence"],"warnings":[],"limitations":["Sender can be spoofed"],"notice":"Not proof of a leak"}"""
        val result = repository(HttpResponse(200, json)) { _, url, body, _ -> path = url; payload = body.orEmpty() }
            .analyzeSms("token", "Example text", "+12345")
        assertEquals("$base/mobile/v1/sms/analyze", path)
        assertTrue(payload.contains("\"sender\":\"+12345\""))
        assertEquals("Not proof of a leak", (result as ApiResult.Success<SmsAnalysisDto>).value.notice)
    }
}
