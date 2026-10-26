package com.codecrafters.leaktrace.ui.sms

import com.codecrafters.leaktrace.data.remote.ExposureDto
import com.codecrafters.leaktrace.data.remote.WebsiteDto
import java.util.Locale

/** Runs only on website rows already fetched for the authenticated account. No SMS is uploaded. */
object SmsNicknameMatcher {
    fun find(text: String, ownedWebsites: List<WebsiteDto>): List<ExposureDto> {
        val counts = ownedWebsites.mapNotNull { it.nickname?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT) }
            .groupingBy { it }.eachCount()
        return ownedWebsites.mapNotNull { website ->
            val nickname = website.nickname?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            // A shared nickname cannot identify one particular website.
            if (counts[nickname.lowercase(Locale.ROOT)] != 1) return@mapNotNull null
            val pattern = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(nickname)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            if (!pattern.containsMatchIn(text)) return@mapNotNull null
            ExposureDto(
                signupId = website.id,
                siteName = website.siteName,
                markerType = "nickname_mention",
                marker = nickname,
                observedSource = null,
                expectedDomain = website.siteDomain,
                confidence = "low",
                explanation = "This SMS uses the nickname $nickname that you recorded for ${website.siteName}. " +
                    "That website is a possible place the name was used; the SMS sender has not been verified.",
                caveat = "A nickname match is a clue, not proof that ${website.siteName} leaked your data or sent this SMS.",
            )
        }
    }
}
