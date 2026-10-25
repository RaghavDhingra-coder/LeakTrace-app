package com.codecrafters.leaktrace.data.remote

import com.codecrafters.leaktrace.BuildConfig

/** One configured mobile API origin, shared by discovery, pairing and authenticated requests. */
object ApiConfig {
    val baseUrl: String = BuildConfig.API_BASE_URL
}

object MobilePaths {
    const val prefix = "/mobile/v1"
    const val pairings = "$prefix/pairings"
    const val me = "$prefix/me"
    const val logout = "$prefix/logout"
    const val websites = "$prefix/websites"
    const val emails = "$prefix/emails"
    const val timeline = "$prefix/timeline"
    const val smsAnalyze = "$prefix/sms/analyze"
    const val smsInvestigations = "$prefix/sms/investigations"
}
