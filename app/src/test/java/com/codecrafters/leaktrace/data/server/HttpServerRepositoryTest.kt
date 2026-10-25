package com.codecrafters.leaktrace.data.server

import com.codecrafters.leaktrace.data.remote.HttpClient
import com.codecrafters.leaktrace.data.remote.HttpResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class HttpServerRepositoryTest {
    private val base = "https://leaktrace.test"
    private val rootOk = HttpResponse(200, """{"application":"LeakTrace","status":"running","version":"0.1.0"}""")
    private val healthOk = HttpResponse(200, """{"status":"ok","message":"LeakTrace running"}""")
    private val notFound = HttpResponse(404, """{"detail":"Not Found"}""")

    private fun repository(responses: Map<String, HttpResponse>, requested: MutableList<String> = mutableListOf()) =
        HttpServerRepository(base, HttpClient { url ->
            synchronized(requested) { requested += url }
            responses[url.removePrefix(base)] ?: throw IOException("unreachable")
        }, clock = { 42L })

    @Test
    fun ingestOnlyServer_isOnlineWithEveryFeatureNotDeployed() = runTest {
        val responses = mapOf("/" to rootOk, "/health" to healthOk) +
            BackendFeature.entries.associate { it.probePath to notFound }

        val report = repository(responses).check()

        assertEquals(ServerHealth.Online("0.1.0"), report.health)
        BackendFeature.entries.forEach { assertEquals(FeatureAvailability.NotDeployed, report.features[it]) }
        assertEquals(42L, report.checkedAtMillis)
    }

    @Test
    fun probeStatusCodes_mapToAvailability() = runTest {
        val responses = mapOf(
            "/" to rootOk, "/health" to healthOk,
            BackendFeature.Account.probePath to HttpResponse(401, ""),
            BackendFeature.Websites.probePath to HttpResponse(403, ""),
            BackendFeature.Email.probePath to notFound,
            BackendFeature.Sms.probePath to HttpResponse(500, ""),
        )

        val features = repository(responses).check().features

        assertEquals(FeatureAvailability.RequiresSignIn, features[BackendFeature.Account])
        assertEquals(FeatureAvailability.LocalOnly, features[BackendFeature.Websites])
        assertEquals(FeatureAvailability.NotDeployed, features[BackendFeature.Email])
        assertEquals(FeatureAvailability.Unknown(500), features[BackendFeature.Sms])
        assertEquals(FeatureAvailability.Unknown(null), features[BackendFeature.Timeline])
    }

    @Test
    fun successfulProbeDoesNotParseOrExposeAccountRecords() = runTest {
        val responses = mapOf("/" to rootOk, "/health" to healthOk) +
            BackendFeature.entries.associate { it.probePath to notFound } +
            (BackendFeature.Websites.probePath to HttpResponse(200, "{\"unconfirmed\":true}"))

        val report = repository(responses).check()

        assertEquals(FeatureAvailability.Unknown(200), report.features[BackendFeature.Websites])
    }

    @Test
    fun unreachableServer_marksEveryFeatureUnknown() = runTest {
        val report = repository(emptyMap()).check()

        assertEquals(ServerHealth.Unreachable, report.health)
        assertTrue(report.features.values.all { it == FeatureAvailability.Unknown(null) })
    }

    @Test
    fun nonLeakTraceRoot_isUnexpected() = runTest {
        val responses = mapOf("/" to HttpResponse(200, """{"application":"Other","status":"running","version":"1"}"""))
        assertEquals(ServerHealth.Unexpected(200), repository(responses).check().health)
    }

    @Test
    fun malformedRoot_isUnexpected() = runTest {
        val responses = mapOf("/" to HttpResponse(200, "<html>proxy error</html>"))
        assertEquals(ServerHealth.Unexpected(200), repository(responses).check().health)
    }

    @Test
    fun rootErrorStatus_isUnexpectedWithThatCode() = runTest {
        assertEquals(ServerHealth.Unexpected(502), repository(mapOf("/" to HttpResponse(502, ""))).check().health)
    }

    @Test
    fun unhealthyHealthEndpoint_isUnexpected() = runTest {
        val responses = mapOf("/" to rootOk, "/health" to HttpResponse(503, ""))
        assertEquals(ServerHealth.Unexpected(503), repository(responses).check().health)
    }

    @Test
    fun onlyRequestsTheConfiguredServer() = runTest {
        val requested = mutableListOf<String>()
        repository(mapOf("/" to rootOk, "/health" to healthOk), requested).check()
        assertTrue(requested.isNotEmpty())
        assertTrue(requested.all { it.startsWith("$base/") })
    }
}
