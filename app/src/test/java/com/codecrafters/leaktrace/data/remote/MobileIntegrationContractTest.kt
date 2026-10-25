package com.codecrafters.leaktrace.data.remote

import com.codecrafters.leaktrace.data.server.*
import com.codecrafters.leaktrace.ui.mobile.*
import com.codecrafters.leaktrace.ui.components.showMobileData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.URI

class MobileIntegrationContractTest {
    private val base = "https://mobile.example"
    private val page = """{"items":[],"total":0,"limit":20,"offset":0}"""
    private val me = """{"email":"alice@example.test","email_verified":true,"scopes":["account:read"],"device":{"id":7,"label":null,"created_at":"2026-10-25T00:00:00Z","last_used_at":"2026-10-25T00:00:00Z","expires_at":"2026-11-24T00:00:00Z"}}"""
    private val token = """{"token":"test-token","expires_at":"2026-11-24T00:00:00Z","email":"alice@example.test","scopes":["account:read"]}"""
    private fun repo(response: HttpResponse, capture: (String, String, String?, String?) -> Unit = { _, _, _, _ -> }) =
        HttpMobileRepository(base, object : HttpClient {
            override suspend fun get(url: String) = response
            override suspend fun request(method: String, url: String, body: String?, bearer: String?): HttpResponse {
                capture(method, url, body, bearer)
                return response
            }
        })

    @Test fun configuredBackendIsAMobileOrigin() {
        val origin = URI(ApiConfig.baseUrl)
        assertEquals("https", origin.scheme)
        assertNotEquals("leaktrace-ingest.onrender.com", origin.host)
        assertEquals("", origin.path)
        assertNull(origin.userInfo)
        assertNull(origin.query)
        assertNull(origin.fragment)
    }

    @Test fun allFiveLiveMobileProbeContractsRequirePairing() = runTest {
        val requests = mutableListOf<String>()
        val report = HttpServerRepository(base, HttpClient { url ->
            requests += url
            when (url.removePrefix(base)) {
                "/" -> HttpResponse(200, """{"application":"LeakTrace","status":"running","version":"0.1.0"}""")
                "/health" -> HttpResponse(200, """{"status":"ok","message":"LeakTrace running"}""")
                "/mobile/v1/me", "/mobile/v1/websites", "/mobile/v1/emails",
                "/mobile/v1/timeline", "/mobile/v1/sms/investigations" -> HttpResponse(401, """{"detail":"Sign in required."}""")
                else -> HttpResponse(404, """{"detail":"Not Found"}""")
            }
        }).check()
        assertEquals(ServerHealth.Online("0.1.0"), report.health)
        assertEquals(5, report.features.size)
        assertTrue(report.features.values.all { it == FeatureAvailability.RequiresSignIn })
        assertEquals(7, requests.size)
    }

    @Test fun accountEmailTimelineAndLogoutUseRealMobilePathsAndBearer() = runTest {
        val sent = mutableListOf<Pair<String,String>>()
        val capture: (String,String,String?,String?) -> Unit = { method,url,_,bearer ->
            assertEquals("test-token", bearer); sent += method to url
        }
        val account = repo(HttpResponse(200, me), capture).me("test-token") as ApiResult.Success
        assertEquals("alice@example.test", account.value.email)
        assertNull(account.value.device.label)
        val emailJson = """{"items":[{"id":1,"received_at":"2026-10-25T00:00:00Z","created_at":"2026-10-25T00:00:00Z","match_status":null,"match_confidence":null,"recipient_known":true,"signup_id":2,"site_name":"Shop","site_domain":null,"sender_domain":null}],"total":1,"limit":20,"offset":0}"""
        assertEquals("Shop", (repo(HttpResponse(200, emailJson), capture).emails("test-token") as ApiResult.Success).value.items.single().siteName)
        val timelineJson = """{"items":[{"channel":"email","id":1,"at":"2026-10-25T00:00:00Z","title":"Shop email","status":null,"site_name":"Shop","signup_id":2,"linked":true}],"notice":"Not proof of a leak"}"""
        assertEquals(true, (repo(HttpResponse(200, timelineJson), capture).timeline("test-token") as ApiResult.Success).value.items.single().linked)
        assertEquals(ApiResult.Success(Unit), repo(HttpResponse(204, ""), capture).logout("test-token"))
        assertEquals(listOf("GET" to "$base/mobile/v1/me", "GET" to "$base/mobile/v1/emails", "GET" to "$base/mobile/v1/timeline", "POST" to "$base/mobile/v1/logout"), sent)
    }

    @Test fun emptyCollectionsAreSuccessAndErrorsStayDistinctForEveryRead() = runTest {
        assertTrue((repo(HttpResponse(200, page)).websites("t") as ApiResult.Success).value.items.isEmpty())
        assertTrue((repo(HttpResponse(200, page)).emails("t") as ApiResult.Success).value.items.isEmpty())
        assertTrue((repo(HttpResponse(200, """{"items":[],"notice":"notice"}""")).timeline("t") as ApiResult.Success).value.items.isEmpty())
        for ((code, expected) in listOf(401 to ApiResult.Unauthorized, 403 to ApiResult.Forbidden, 404 to ApiResult.NotDeployed, 429 to ApiResult.RateLimited, 503 to ApiResult.ServerError(503), 200 to ApiResult.InvalidResponse)) {
            val repository = repo(HttpResponse(code, "{}"))
            assertEquals(expected, repository.me("t"))
            assertEquals(expected, repository.websites("t"))
            assertEquals(expected, repository.emails("t"))
            assertEquals(expected, repository.timeline("t"))
            assertEquals(expected, repository.analyzeSms("t", "hello", null))
        }
        val offline = HttpMobileRepository(base, object: HttpClient {
            override suspend fun get(url: String): HttpResponse = throw IOException()
            override suspend fun request(method: String, url: String, body: String?, bearer: String?): HttpResponse = throw IOException()
        })
        assertEquals(ApiResult.NetworkError, offline.me("t"))
        assertEquals(ApiResult.NetworkError, offline.websites("t"))
        assertEquals(ApiResult.NetworkError, offline.emails("t"))
        assertEquals(ApiResult.NetworkError, offline.timeline("t"))
    }

    @Test fun pairingExchangeKeepsResource404DistinctFromMissingApi() = runTest {
        assertEquals(ApiResult.PairingNotFound, repo(HttpResponse(404, """{"detail":"Unknown pairing."}""")).exchange(9, "secret"))
        assertEquals(ApiResult.Expired, repo(HttpResponse(410, "")).exchange(9, "secret"))
        assertEquals(ApiResult.Pending, repo(HttpResponse(202, "")).exchange(9, "secret"))
        val result = repo(HttpResponse(200, token)) { method,url,body,bearer ->
            assertEquals("POST", method)
            assertEquals("$base/mobile/v1/pairings/9/token", url)
            assertEquals("""{"secret":"secret"}""", body)
            assertNull(bearer)
        }.exchange(9, "secret") as ApiResult.Success
        assertEquals("test-token", result.value.token)
    }

    @Test fun staleProbesCannotHideAuthenticatedResultsOrErrors() {
        for (probe in listOf(null, FeatureAvailability.Unknown(null), FeatureAvailability.Unknown(503), FeatureAvailability.NotDeployed)) {
            for (state in listOf(MobileLoadState.Loading, MobileLoadState.Empty, MobileLoadState.Ready("data"), MobileLoadState.Error(MobileFailure.Network), MobileLoadState.SessionExpired, MobileLoadState.Unavailable)) {
                assertTrue(showMobileData(state, probe))
            }
            assertFalse(showMobileData(MobileLoadState.NeedsAccount, probe))
        }
        assertTrue(showMobileData(MobileLoadState.NeedsAccount, FeatureAvailability.RequiresSignIn))
    }

    @Test fun credentialsAreBoundToTheirServerOrigin() {
        assertNotEquals(credentialNamespace("https://one.example"), credentialNamespace("https://two.example"))
        assertEquals(credentialNamespace("https://one.example"), credentialNamespace("https://one.example/"))
        assertFalse(credentialNamespace("https://one.example").contains("one.example"))
    }
}
