package com.codecrafters.leaktrace.ui.sms

import com.codecrafters.leaktrace.data.remote.WebsiteDto
import com.codecrafters.leaktrace.data.remote.WebsitePageDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsNicknameMatcherTest {
    private fun site(id: Long, name: String, nickname: String?) = WebsiteDto(
        id, name, "${name.lowercase()}.com", null, nickname, false, 0, 0, "2026-01-01T00:00:00Z",
    )
    private val facebook = site(1, "Facebook", "Ember")
    private fun analyzed(text: String, sites: List<WebsiteDto>) = SmsInvestigationViewModel().apply {
        onTextChange(text)
        analyze(WebsitePageDto(sites, sites.size, 50, 0))
    }.analysis.value as SmsAnalysisState.Complete

    @Test fun greetingNicknameShowsFacebookEvenWithoutLinkOrWarningKeyword() {
        val result = analyzed("Hi Ember, win today 1 crore", listOf(facebook)).analysis
        assertTrue(result.urls.isEmpty())
        assertTrue(result.indicators.isEmpty())
        assertEquals("Facebook", result.possibleSources.single().siteName)
        assertEquals("nickname_mention", result.possibleSources.single().markerType)
        assertTrue(result.possibleSources.single().caveat.contains("not proof"))
    }

    @Test fun absentNicknameDoesNotSuggestSite() {
        assertTrue(analyzed("Hi Alex, win today 1 crore", listOf(facebook)).analysis.possibleSources.isEmpty())
    }

    @Test fun caseAndPunctuationMatch() {
        assertEquals("Facebook", analyzed("HI eMbEr!", listOf(facebook)).analysis.possibleSources.single().siteName)
    }

    @Test fun partialWordsDoNotMatch() {
        assertTrue(analyzed("Hi Embers, win today", listOf(facebook)).analysis.possibleSources.isEmpty())
        assertTrue(analyzed("Remember this", listOf(facebook)).analysis.possibleSources.isEmpty())
    }

    @Test fun noRegisteredAssociationDoesNotInventSource() {
        assertTrue(analyzed("Hi Ember, win today", emptyList()).analysis.possibleSources.isEmpty())
    }

    @Test fun onlyAuthenticatedOwnersRowsAreCompared() {
        val anotherUsersSite = site(2, "Amazon", "Ember")
        val result = analyzed("Hi Ember, win today", listOf(facebook)).analysis
        assertEquals(listOf(1L), result.possibleSources.map { it.signupId })
        assertFalse(result.possibleSources.any { it.signupId == anotherUsersSite.id })
    }

    @Test fun ambiguousNicknameIsNotAssignedToOneSite() {
        assertTrue(analyzed("Hi Ember!", listOf(facebook, site(2, "Amazon", "ember"))).analysis.possibleSources.isEmpty())
    }

    @Test fun accountSwitchRemovesPreviousUsersFinding() {
        val model = SmsInvestigationViewModel()
        model.onTextChange("Hi Ember, win today 1 crore")
        model.analyze(WebsitePageDto(listOf(facebook), 1, 50, 0))
        assertEquals("Facebook", (model.analysis.value as SmsAnalysisState.Complete).analysis.possibleSources.single().siteName)
        model.updateAccountComparison(null)
        assertTrue((model.analysis.value as SmsAnalysisState.Complete).analysis.possibleSources.isEmpty())
        model.updateAccountComparison(WebsitePageDto(listOf(site(2, "Amazon", "Alice")), 1, 50, 0))
        assertTrue((model.analysis.value as SmsAnalysisState.Complete).analysis.possibleSources.isEmpty())
    }

    @Test fun signedOutStandaloneAnalysisHasNoAccountComparison() {
        val model = SmsInvestigationViewModel()
        model.onTextChange("Hi Ember")
        model.analyze()
        val result = (model.analysis.value as SmsAnalysisState.Complete).analysis
        assertFalse(result.accountComparisonAvailable)
        assertTrue(result.possibleSources.isEmpty())
    }
}
