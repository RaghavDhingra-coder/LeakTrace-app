package com.codecrafters.leaktrace.ui.sms

import java.net.URI
import java.util.Locale
import com.codecrafters.leaktrace.data.remote.ExposureDto

enum class SmsIndicator {
    Urgency,
    AccountThreat,
    SensitiveInfoRequest,
    PaymentOrPrize,
    InsecureLink,
    ShortenedLink,
    IpAddressLink,
    MisleadingLink,
}

data class SmsAnalysis(
    val urls: List<String>,
    val indicators: List<SmsIndicator>,
    val possibleSources: List<ExposureDto> = emptyList(),
    val accountComparisonAvailable: Boolean = false,
    val accountComparisonComplete: Boolean = true,
)

sealed interface SmsAnalysisState {
    data object NotAnalyzed : SmsAnalysisState
    data object EmptyInput : SmsAnalysisState
    data object TooLong : SmsAnalysisState
    data class Complete(val analysis: SmsAnalysis) : SmsAnalysisState
}

/** Local text heuristics only: no network, reputation lookup, or claim that a message is fraudulent. */
object SmsLocalAnalyzer {
    private val urlPattern = Regex("""(?i)(?<![\w@])(?:https?://|www\.)[^\s<>"'“”]+""")
    private val trailingPunctuation = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
    private val urgency = Regex("""\b(?:urgent|immediately|act now|last chance|within\s+\d+\s+(?:minutes?|hours?)|expires?\s+(?:today|soon))\b""", RegexOption.IGNORE_CASE)
    private val accountThreat = Regex("""\b(?:account|card|service)\s+(?:(?:has been|will be|is)\s+)?(?:suspended|locked|blocked|closed)\b|\bunusual activity\b""", RegexOption.IGNORE_CASE)
    private val sensitiveInfoRequest = Regex("""\b(?:send|share|reply\s+with|enter|provide|confirm|submit|verify)\b.{0,40}\b(?:password|passcode|one[ -]time\s+(?:code|password)|otp|cvv|pin|verification\s+code|card\s+number|bank\s+details)\b""", RegexOption.IGNORE_CASE)
    private val negatedInstruction = Regex("""(?:do\s+not|don't|never)\s+$""", RegexOption.IGNORE_CASE)
    private val paymentOrPrize = Regex("""\b(?:pay|send)\b.{0,25}\b(?:fee|deposit|money|amount)\b|\b(?:claim|collect)\b.{0,30}\b(?:prize|reward|winnings)\b""", RegexOption.IGNORE_CASE)
    private val ipv4 = Regex("""\d{1,3}(?:\.\d{1,3}){3}""")
    private val shorteners = setOf("bit.ly", "tinyurl.com", "t.co", "cutt.ly", "is.gd", "ow.ly", "buff.ly", "rebrand.ly", "shorturl.at")

    fun analyze(text: String): SmsAnalysisState {
        if (text.length > SmsDraftState.MAX_SMS_LENGTH) return SmsAnalysisState.TooLong
        if (text.isBlank()) return SmsAnalysisState.EmptyInput

        val urls = urlPattern.findAll(text).mapNotNull { match ->
            val candidate = match.value.trimEnd(*trailingPunctuation)
            val parsed = parseUrl(candidate) ?: return@mapNotNull null
            candidate to parsed
        }.distinctBy { it.first }.toList()

        val indicators = linkedSetOf<SmsIndicator>()
        if (urgency.containsMatchIn(text)) indicators += SmsIndicator.Urgency
        if (accountThreat.containsMatchIn(text)) indicators += SmsIndicator.AccountThreat
        if (sensitiveInfoRequest.findAll(text).any { match ->
                val before = text.substring((match.range.first - 16).coerceAtLeast(0), match.range.first)
                !negatedInstruction.containsMatchIn(before)
            }) {
            indicators += SmsIndicator.SensitiveInfoRequest
        }
        if (paymentOrPrize.containsMatchIn(text)) indicators += SmsIndicator.PaymentOrPrize
        urls.forEach { (_, uri) ->
            val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.").orEmpty()
            if (uri.scheme.equals("http", ignoreCase = true)) indicators += SmsIndicator.InsecureLink
            if (host in shorteners) indicators += SmsIndicator.ShortenedLink
            if (ipv4.matches(host) || ':' in host) indicators += SmsIndicator.IpAddressLink
            if (uri.rawUserInfo != null) indicators += SmsIndicator.MisleadingLink
        }
        return SmsAnalysisState.Complete(SmsAnalysis(urls.map { it.first }, indicators.toList()))
    }

    private fun parseUrl(candidate: String): URI? = try {
        val normalized = if (candidate.startsWith("www.", ignoreCase = true)) "https://$candidate" else candidate
        URI(normalized).takeIf { uri ->
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
                !uri.host.isNullOrBlank()
        }
    } catch (_: IllegalArgumentException) {
        null
    }
}
