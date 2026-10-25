package com.codecrafters.leaktrace.data.server

import com.codecrafters.leaktrace.data.remote.HealthDto
import com.codecrafters.leaktrace.data.remote.HttpClient
import com.codecrafters.leaktrace.data.remote.LeakTraceJson
import com.codecrafters.leaktrace.data.remote.RootDto
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerializationException
import java.io.IOException

interface ServerRepository {
    val baseUrl: String
    suspend fun check(): ServerReport
}

/**
 * Checks the LeakTrace backend using only routes that need no credentials: GET / and
 * GET /health for liveness, and an unauthenticated GET of each feature's route to learn
 * whether this server provides it. Never sends cookies, tokens or user data.
 */
class HttpServerRepository(
    override val baseUrl: String,
    private val http: HttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : ServerRepository {

    override suspend fun check(): ServerReport = coroutineScope {
        val health = async { checkHealth() }
        val features = BackendFeature.entries.map { feature -> async { feature to probe(feature) } }
        val resolvedHealth = health.await()
        val resolvedFeatures = if (resolvedHealth is ServerHealth.Unreachable) {
            BackendFeature.entries.associateWith { FeatureAvailability.Unknown(null) }
        } else {
            features.awaitAll().toMap()
        }
        ServerReport(baseUrl, resolvedHealth, resolvedFeatures, clock())
    }

    private suspend fun checkHealth(): ServerHealth {
        return try {
            val root = http.get("$baseUrl/")
            if (root.code != 200) return ServerHealth.Unexpected(root.code)
            val rootDto = LeakTraceJson.decodeFromString<RootDto>(root.body)
            if (rootDto.application != "LeakTrace" || rootDto.status != "running") {
                return ServerHealth.Unexpected(root.code)
            }
            val health = http.get("$baseUrl/health")
            if (health.code != 200) return ServerHealth.Unexpected(health.code)
            if (LeakTraceJson.decodeFromString<HealthDto>(health.body).status != "ok") {
                return ServerHealth.Unexpected(health.code)
            }
            ServerHealth.Online(rootDto.version)
        } catch (_: IOException) {
            ServerHealth.Unreachable
        } catch (_: SerializationException) {
            ServerHealth.Unexpected(200)
        } catch (_: IllegalArgumentException) {
            ServerHealth.Unexpected(200)
        }
    }

    private suspend fun probe(feature: BackendFeature): FeatureAvailability = try {
        when (val code = http.get(baseUrl + feature.probePath).code) {
            401 -> FeatureAvailability.RequiresSignIn
            403 -> FeatureAvailability.LocalOnly
            404 -> FeatureAvailability.NotDeployed
            else -> FeatureAvailability.Unknown(code)
        }
    } catch (_: IOException) {
        FeatureAvailability.Unknown(null)
    }
}
