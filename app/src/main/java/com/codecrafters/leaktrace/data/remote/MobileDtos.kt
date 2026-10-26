package com.codecrafters.leaktrace.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable class MobileLoginDto(val email: String, val password: String, @SerialName("device_label") val deviceLabel: String = "Android device")

// Confirmed by backend/routes/mobile.py, backend/schemas/dashboard.py and backend/schemas/sms.py.
// Times are ISO-8601 strings; the API does not use epoch milliseconds.
@Serializable data class PairingStartDto(@SerialName("secret_hash") val secretHash: String, @SerialName("device_label") val deviceLabel: String? = null)
@Serializable data class PairingStartedDto(@SerialName("pairing_id") val pairingId: Long, val code: String, @SerialName("expires_at") val expiresAt: String)
@Serializable data class PairingExchangeDto(val secret: String)
@Serializable data class MobileTokenDto(val token: String, @SerialName("expires_at") val expiresAt: String, val email: String, val scopes: List<String>)
@Serializable data class MobileDeviceDto(val id: Long, val label: String?, @SerialName("created_at") val createdAt: String, @SerialName("last_used_at") val lastUsedAt: String, @SerialName("expires_at") val expiresAt: String)
@Serializable data class MobileMeDto(val email: String, @SerialName("email_verified") val emailVerified: Boolean, val scopes: List<String>, val device: MobileDeviceDto)

@Serializable data class WebsiteDto(
    val id: Long,
    @SerialName("site_name") val siteName: String,
    @SerialName("site_domain") val siteDomain: String?,
    val alias: String?,
    val nickname: String?,
    @SerialName("real_name_required") val realNameRequired: Boolean,
    @SerialName("receipt_count") val receiptCount: Int,
    @SerialName("email_count") val emailCount: Int,
    @SerialName("created_at") val createdAt: String,
)
@Serializable data class WebsitePageDto(val items: List<WebsiteDto>, val total: Int, val limit: Int, val offset: Int)

@Serializable data class ExposureDto(
    @SerialName("signup_id") val signupId: Long,
    @SerialName("site_name") val siteName: String,
    @SerialName("marker_type") val markerType: String,
    val marker: String,
    @SerialName("observed_source") val observedSource: String?,
    @SerialName("expected_domain") val expectedDomain: String?,
    val confidence: String,
    val explanation: String,
    val caveat: String,
)

@Serializable data class EmailDto(
    val exposure: List<ExposureDto> = emptyList(),
    val status: String? = null,
    @SerialName("status_reason") val statusReason: String? = null,
    val alias: String? = null,
    val unread: Boolean = false,
    val tracked: Boolean = false,
    val subject: String? = null,
    @SerialName("sender_name") val senderName: String? = null,
    @SerialName("sender_email") val senderEmail: String? = null,
    @SerialName("auth_results") val authResults: String? = null,
    val id: Long,
    @SerialName("received_at") val receivedAt: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("match_status") val matchStatus: String?,
    @SerialName("match_confidence") val matchConfidence: String?,
    @SerialName("recipient_known") val recipientKnown: Boolean,
    @SerialName("signup_id") val signupId: Long?,
    @SerialName("site_name") val siteName: String?,
    @SerialName("site_domain") val siteDomain: String?,
    @SerialName("sender_domain") val senderDomain: String?,
)
@Serializable data class EmailPageDto(val items: List<EmailDto>, val total: Int, val limit: Int, val offset: Int)

@Serializable data class TimelineItemDto(
    val channel: String,
    val id: Long,
    val at: String,
    val title: String,
    val status: String?,
    @SerialName("site_name") val siteName: String?,
    @SerialName("signup_id") val signupId: Long?,
    val linked: Boolean,
)
@Serializable data class TimelineDto(val items: List<TimelineItemDto>, val notice: String)

@Serializable data class SmsAnalyzeRequestDto(val text: String, val sender: String? = null)
@Serializable data class SmsSenderDto(val type: String, val display: String)
@Serializable data class SmsEntityDto(
    val key: String, val type: String, val display: String, val value: String?,
    val certainty: String, val sensitive: Boolean, val note: String?, val excluded: Boolean,
    val relation: String?, @SerialName("signup_id") val signupId: Long?,
)
@Serializable data class SmsSignalDto(val type: String, val explanation: String, val excerpt: String?, val weak: Boolean)
@Serializable data class SmsCandidateDto(
    val key: String,
    val name: String,
    val domain: String?,
    @SerialName("signup_id") val signupId: Long?,
    val status: String,
    val signals: List<SmsSignalDto>,
    val unknowns: List<String>,
    @SerialName("email_count") val emailCount: Int?,
    @SerialName("last_email_at") val lastEmailAt: String?,
    @SerialName("sms_count") val smsCount: Int?,
)
@Serializable data class SmsRiskReasonDto(val code: String, val message: String, val evidence: String? = null)
@Serializable data class SmsRiskDto(val assessment: String, @SerialName("risk_level") val riskLevel: String, val reasons: List<SmsRiskReasonDto>, val exposure: List<ExposureDto> = emptyList(), val urls: List<String>, val domains: List<String>, @SerialName("next_steps") val nextSteps: List<String>, val notice: String)

@Serializable data class SmsAnalysisDto(
    val risk: SmsRiskDto? = null,
    val status: String,
    val sender: SmsSenderDto?,
    @SerialName("sender_notes") val senderNotes: List<String>,
    @SerialName("received_at") val receivedAt: String?,
    val label: String?,
    @SerialName("redacted_text") val redactedText: String,
    val entities: List<SmsEntityDto>,
    val candidates: List<SmsCandidateDto>,
    val missing: List<String>,
    val warnings: List<String>,
    val limitations: List<String>,
    val notice: String,
)

/** Never infer attribution from a legacy heuristic match_status. */
fun EmailDto.attributionLabel(): String = when (status) {
    "matched" -> "Matched"
    "unmatched" -> "Unmatched"
    "failed" -> "Failed"
    "unprocessed" -> "Unprocessed"
    else -> "Attribution unavailable on this server"
}

@Serializable data class CallAnalyzeRequestDto(
    val transcript: String,
    val save: Boolean = false,
    @SerialName("store_redacted_transcript") val storeRedactedTranscript: Boolean = false,
)
@Serializable data class CallAnalysisDto(
    val id: Long? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val status: String,
    @SerialName("redacted_transcript") val redactedTranscript: String,
    val entities: List<SmsEntityDto>,
    val candidates: List<SmsCandidateDto>,
    val exposure: List<ExposureDto>,
    val statements: List<SmsRiskReasonDto>,
    val missing: List<String>,
    val notice: String,
)
@Serializable data class CallAnalysisPageDto(val items: List<CallAnalysisDto>, val total: Int, val limit: Int, val offset: Int)
