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

    @Test fun websitesFetchEveryOwnedPageBeforeNicknameComparison() = runTest {
        val paths = mutableListOf<String>()
        val repo = HttpMobileRepository(base, object : HttpClient {
            override suspend fun get(url: String) = error("Bearer token required")
            override suspend fun request(method: String, url: String, body: String?, bearer: String?): HttpResponse {
                assertEquals("test-token", bearer)
                paths += url
                val offset = if (url.contains("offset=1")) 1 else 0
                val site = if (offset == 0) "Shop" else "Facebook"
                return HttpResponse(200, """{"items":[{"id":${offset + 1},"site_name":"$site","site_domain":"site.example","alias":null,"nickname":"Ember","real_name_required":false,"receipt_count":0,"email_count":0,"created_at":"2026-01-01T00:00:00Z"}],"total":2,"limit":1,"offset":$offset}""")
            }
        })
        val result = repo.websites("test-token") as ApiResult.Success<WebsitePageDto>
        assertEquals(listOf("Facebook", "Shop"), result.value.items.map { it.siteName })
        assertEquals("$base/mobile/v1/websites?limit=100&offset=1", paths[1])
    }

    @Test fun emailsFetchOlderPagesKeepNewestFirstAndDeduplicateShiftedRows() = runTest {
        val requests = mutableListOf<String>()
        fun row(id: Int, time: String, exposure: String = "[]") = """{"id":$id,"received_at":"$time","created_at":"$time","match_status":null,"match_confidence":null,"recipient_known":true,"signup_id":1,"site_name":"Facebook","site_domain":"facebook.com","sender_domain":"gmail.com","status":"matched","alias":"a@leaktrap.xyz","unread":true,"tracked":true,"subject":"Hello","sender_email":"mayur@gmail.com","exposure":$exposure}"""
        val finding = """[{"signup_id":1,"site_name":"Facebook","marker_type":"recipient_alias","marker":"a@leaktrap.xyz","observed_source":"gmail.com","expected_domain":"facebook.com","confidence":"medium","explanation":"Possible source","caveat":"Not proof"}]"""
        val repo = HttpMobileRepository(base, object : HttpClient {
            override suspend fun get(url: String) = error("Bearer required")
            override suspend fun request(method: String, url: String, body: String?, bearer: String?): HttpResponse {
                assertEquals("alice-token", bearer)
                requests += url
                val page = if (url.contains("offset=2")) {
                    """{"items":[${row(2,"2026-01-02T00:00:00Z")},${row(1,"2026-01-01T00:00:00Z")}],"total":3,"limit":100,"offset":2}"""
                } else {
                    """{"items":[${row(3,"2026-01-03T00:00:00Z",finding)},${row(2,"2026-01-02T00:00:00Z")}],"total":3,"limit":2,"offset":0}"""
                }
                return HttpResponse(200, page)
            }
        })
        val emails = (repo.emails("alice-token") as ApiResult.Success).value.items
        assertEquals(listOf(3L, 2L, 1L), emails.map { it.id })
        assertEquals("Facebook", emails.first().exposure.single().siteName)
        assertEquals(2, requests.size)
        assertEquals("$base/mobile/v1/emails?limit=20&offset=2", requests[1])
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
