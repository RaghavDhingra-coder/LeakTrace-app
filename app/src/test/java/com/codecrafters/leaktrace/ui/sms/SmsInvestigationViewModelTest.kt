package com.codecrafters.leaktrace.ui.sms

import com.codecrafters.leaktrace.ui.sms.SmsDraftState.Companion.MAX_SMS_LENGTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsInvestigationViewModelTest {
    @Test
    fun sharedText_replacesDraftTrimmed() {
        val viewModel = SmsInvestigationViewModel()
        viewModel.onTextChange("old")
        viewModel.onSharedText("  Your OTP is 123456  \n")
        assertEquals("Your OTP is 123456", viewModel.draft.value.text)
    }

    @Test
    fun lengthLimit_boundsLocalAnalysis() {
        val viewModel = SmsInvestigationViewModel()
        viewModel.onTextChange("a".repeat(MAX_SMS_LENGTH))
        assertFalse(viewModel.draft.value.isTooLong)
        viewModel.onTextChange("a".repeat(MAX_SMS_LENGTH + 1))
        assertTrue(viewModel.draft.value.isTooLong)
    }

    @Test
    fun clear_emptiesDraft() {
        val viewModel = SmsInvestigationViewModel()
        viewModel.onTextChange("hello")
        viewModel.clear()
        assertTrue(viewModel.draft.value.isBlank)
    }

    @Test
    fun analyzeUsesCurrentDraftAndEditingClearsStaleResults() {
        val viewModel = SmsInvestigationViewModel()
        viewModel.onTextChange("Act now at https://example.com")
        viewModel.analyze()
        assertTrue(viewModel.analysis.value is SmsAnalysisState.Complete)

        viewModel.onTextChange("A different message")
        assertEquals(SmsAnalysisState.NotAnalyzed, viewModel.analysis.value)
    }

    @Test
    fun sharedTextAndClearResetPreviousAnalysis() {
        val viewModel = SmsInvestigationViewModel()
        viewModel.onTextChange("Pay a fee")
        viewModel.analyze()
        viewModel.onSharedText("  New shared message  ")
        assertEquals("New shared message", viewModel.draft.value.text)
        assertEquals(SmsAnalysisState.NotAnalyzed, viewModel.analysis.value)

        viewModel.analyze()
        viewModel.clear()
        assertEquals(SmsAnalysisState.NotAnalyzed, viewModel.analysis.value)
        assertTrue(viewModel.draft.value.isBlank)
    }

    @Test
    fun emptyDraftShowsValidationState() {
        val viewModel = SmsInvestigationViewModel()
        viewModel.analyze()
        assertEquals(SmsAnalysisState.EmptyInput, viewModel.analysis.value)

        viewModel.onTextChange("a".repeat(MAX_SMS_LENGTH + 1))
        viewModel.analyze()
        assertEquals(SmsAnalysisState.TooLong, viewModel.analysis.value)
    }
}
