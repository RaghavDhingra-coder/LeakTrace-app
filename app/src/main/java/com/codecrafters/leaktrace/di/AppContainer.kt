package com.codecrafters.leaktrace.di

import android.content.Context
import com.codecrafters.leaktrace.data.remote.ApiConfig
import com.codecrafters.leaktrace.data.remote.HttpMobileRepository
import com.codecrafters.leaktrace.data.remote.KeystoreMobileCredentialStore
import com.codecrafters.leaktrace.data.remote.MobileCredentialStore
import com.codecrafters.leaktrace.data.remote.MobileRepository
import com.codecrafters.leaktrace.data.remote.UrlConnectionHttpClient
import com.codecrafters.leaktrace.data.server.HttpServerRepository
import com.codecrafters.leaktrace.data.server.ServerRepository

/** Manual dependency wiring; one instance lives in [com.codecrafters.leaktrace.LeakTraceApplication]. */
class AppContainer(context: Context) {
    private val http = UrlConnectionHttpClient()

    val serverRepository: ServerRepository = HttpServerRepository(ApiConfig.baseUrl, http)
    val mobileRepository: MobileRepository = HttpMobileRepository(ApiConfig.baseUrl, http)
    val credentialStore: MobileCredentialStore = KeystoreMobileCredentialStore(context, ApiConfig.baseUrl)
}
