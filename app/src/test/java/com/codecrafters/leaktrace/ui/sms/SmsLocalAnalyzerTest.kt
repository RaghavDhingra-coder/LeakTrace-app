package com.codecrafters.leaktrace.ui.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsLocalAnalyzerTest {
    private fun complete(text: String) = (SmsLocalAnalyzer.analyze(text) as SmsAnalysisState.Complete).analysis

    @Test fun extractsAndDeduplicatesHttpHttpsAndWwwLinks() {
        val analysis = complete("Visit https://example.com/a?x=1, then www.bank.test/login. Again https://example.com/a?x=1!")

        assertEquals(listOf("https://example.com/a?x=1", "www.bank.test/login"), analysis.urls)
    }

    @Test fun ignoresMalformedLinksAndWwwInsideEmailAddresses() {
        val analysis = complete("Broken https:// and user@www.example.com; javascript:alert(1)")

        assertTrue(analysis.urls.isEmpty())
    }

    @Test fun detectsCommonTextAndLinkIndicatorsWithExplanationsAvailableToUi() {
        val analysis = complete("URGENT: your account will be locked within 2 hours. Enter your OTP at http://bit.ly/check")

        assertTrue(analysis.indicators.containsAll(listOf(
            SmsIndicator.Urgency,
            SmsIndicator.AccountThreat,
            SmsIndicator.SensitiveInfoRequest,
            SmsIndicator.InsecureLink,
            SmsIndicator.ShortenedLink,
        )))
    }

    @Test fun detectsPaymentPrizeAndNumericAddress() {
        val analysis = complete("Claim your prize and pay a fee at https://192.0.2.1/claim")

        assertTrue(SmsIndicator.PaymentOrPrize in analysis.indicators)
        assertTrue(SmsIndicator.IpAddressLink in analysis.indicators)
    }

    @Test fun detectsMisleadingAtSignInLink() {
        val analysis = complete("Open https://bank.example@other.test/login")

        assertEquals(listOf("https://bank.example@other.test/login"), analysis.urls)
        assertTrue(SmsIndicator.MisleadingLink in analysis.indicators)
    }

    @Test fun ordinaryOtpNotificationDoesNotCountAsARequestToShareIt() {
        val analysis = complete("Your OTP is 123456. Do not share it with anyone.")

        assertFalse(SmsIndicator.SensitiveInfoRequest in analysis.indicators)
        assertFalse(SmsIndicator.SensitiveInfoRequest in complete("Never share your OTP with anyone.").indicators)
    }

    @Test fun noPatternFoundDoesNotManufactureAWarning() {
        val analysis = complete("Your appointment is at 3 pm tomorrow.")

        assertTrue(analysis.urls.isEmpty())
        assertTrue(analysis.indicators.isEmpty())
    }

    @Test fun blankAndOverlongMessagesAreRejected() {
        assertEquals(SmsAnalysisState.EmptyInput, SmsLocalAnalyzer.analyze(" \n "))
        assertEquals(SmsAnalysisState.TooLong, SmsLocalAnalyzer.analyze("a".repeat(SmsDraftState.MAX_SMS_LENGTH + 1)))
    }
}
