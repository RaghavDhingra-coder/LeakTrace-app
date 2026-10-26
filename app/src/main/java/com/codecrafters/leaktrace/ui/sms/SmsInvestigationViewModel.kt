package com.codecrafters.leaktrace.ui.sms

import androidx.lifecycle.ViewModel
import com.codecrafters.leaktrace.data.remote.WebsitePageDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SmsDraftState(val text: String = "") {
    val length: Int get() = text.length
    val isTooLong: Boolean get() = text.length > MAX_SMS_LENGTH
    val isBlank: Boolean get() = text.isBlank()

    companion object {
        /** Bounds local analysis work and keeps a shared message small enough for the screen. */
        const val MAX_SMS_LENGTH = 2000
    }
}

/**
 * Holds the message the user pasted or shared into LeakTrace. Kept in memory only: it is not
 * saved to disk. Server comparison, when explicitly requested, is handled separately by
 * MobileViewModel; this local draft is gone when the app process ends.
 */
class SmsInvestigationViewModel : ViewModel() {
    private val _draft = MutableStateFlow(SmsDraftState())
    val draft: StateFlow<SmsDraftState> = _draft.asStateFlow()
    private val _analysis = MutableStateFlow<SmsAnalysisState>(SmsAnalysisState.NotAnalyzed)
    val analysis: StateFlow<SmsAnalysisState> = _analysis.asStateFlow()

    fun onTextChange(text: String) {
        _draft.update { it.copy(text = text) }
        _analysis.value = SmsAnalysisState.NotAnalyzed
    }

    /** Text shared from another app replaces the current draft. */
    fun onSharedText(text: String) {
        _draft.update { it.copy(text = text.trim()) }
        _analysis.value = SmsAnalysisState.NotAnalyzed
    }

    fun analyze(websites: WebsitePageDto? = null) {
        val result = SmsLocalAnalyzer.analyze(_draft.value.text)
        _analysis.value = if (result is SmsAnalysisState.Complete && websites != null) {
            result.copy(analysis = result.analysis.copy(
                possibleSources = SmsNicknameMatcher.find(_draft.value.text, websites.items),
                accountComparisonAvailable = true,
                accountComparisonComplete = websites.items.size >= websites.total,
            ))
        } else result
    }

    /** Re-evaluate a completed local draft when account records change; never sends the text. */
    fun updateAccountComparison(websites: WebsitePageDto?) {
        if (_analysis.value is SmsAnalysisState.Complete) analyze(websites)
    }

    fun clear() {
        _draft.value = SmsDraftState()
        _analysis.value = SmsAnalysisState.NotAnalyzed
    }
}
