package com.codecrafters.leaktrace.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Contracts from the reference backend (backend/main.py RootResponse, backend/routes/health.py
// HealthResponse). Verified against the hosted service with unauthenticated GETs.

@Serializable
data class RootDto(val application: String, val status: String, val version: String)

@Serializable
data class HealthDto(val status: String, val message: String)

internal val LeakTraceJson = Json { ignoreUnknownKeys = true }
